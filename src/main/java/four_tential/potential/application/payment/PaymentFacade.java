package four_tential.potential.application.payment;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.OrderExceptionEnum;
import four_tential.potential.common.exception.domain.PaymentExceptionEnum;
import four_tential.potential.domain.course.course.Course;
import four_tential.potential.domain.course.course.CourseRepository;
import four_tential.potential.domain.order.Order;
import four_tential.potential.domain.order.OrderRepository;
import four_tential.potential.domain.order.OrderStatus;
import four_tential.potential.domain.payment.entity.Payment;
import four_tential.potential.domain.payment.entity.Webhook;
import four_tential.potential.domain.payment.port.PaymentGateway;
import four_tential.potential.domain.payment.port.PaymentGatewayRequest;
import four_tential.potential.domain.payment.port.PaymentGatewayResponse;
import four_tential.potential.infra.portone.PortOneWebhookHandler;
import four_tential.potential.presentation.payment.dto.PaymentCreateRequest;
import four_tential.potential.presentation.payment.dto.PaymentCreateResponse;
import io.portone.sdk.server.errors.WebhookVerificationException;
import io.portone.sdk.server.webhook.WebhookTransaction;
import io.portone.sdk.server.webhook.WebhookTransactionData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentFacade {

    private static final Long NO_DISCOUNT = 0L;
    private static final int WEBHOOK_PAYMENT_LOOKUP_RETRY_COUNT = 15;
    private static final long WEBHOOK_PAYMENT_LOOKUP_RETRY_INTERVAL_MILLIS = 200L;

    private final PaymentService paymentService;
    private final WebhookService webhookService;
    private final PaymentGateway paymentGateway;
    private final PortOneWebhookHandler portOneWebhookHandler;
    private final TransactionTemplate transactionTemplate;
    private final OrderRepository orderRepository;
    private final CourseRepository courseRepository;
    private final PaymentDistributedLockExecutor paymentLockExecutor;

    public PaymentCreateResponse createPayment(UUID memberId, PaymentCreateRequest request) {
        // PortOne 조회는 외부 API 호출이므로 분산락 밖에서 먼저 수행
        PaymentGatewayResponse gatewayResponse = paymentGateway.getPayment(request.pgKey());

        Payment payment;
        try {
            // 같은 주문으로 결제 생성 요청이 동시에 들어오는 것을 방지
            payment = paymentLockExecutor.executeWithOrderLock(
                    request.orderId(),
                    () -> createPaymentInOrderLock(memberId, request, gatewayResponse)
            );
        } catch (RuntimeException e) {
            // 서버 검증에 실패하면 이미 승인된 PortOne 결제 취소 요청
            cancelGatewayPaymentSafely(request.pgKey(), gatewayResponse.totalAmount(), "PAYMENT_CREATE_REJECTED");
            throw e;
        }

        // 결제 생성 API보다 Paid 웹훅이 먼저 도착해 PENDING으로 대기 중이면 즉시 확정 처리
        completeAlreadyReceivedPaidWebhook(payment.getPgKey());
        return PaymentCreateResponse.from(payment);
    }

    public void handleWebhook(
            String rawBody,
            String webhookId,
            String webhookTimestamp,
            String webhookSignature) throws WebhookVerificationException {

        // 이미 성공 처리된 webhook-id는 PortOne 재전송으로 보고 멱등하게 무시
        if (webhookService.isCompleted(webhookId)) {
            log.info("[PORTONE_WEBHOOK] completed webhook ignored. id={}", webhookId);
            return;
        }

        // 수신 기록은 비즈니스 처리보다 먼저 남김
        Webhook webhook = transactionTemplate.execute(
                status -> webhookService.receive(webhookId, "UNKNOWN"));

        if (webhook != null && webhook.isCompleted()) {
            log.info("[PORTONE_WEBHOOK] completed webhook ignored after receive. id={}", webhookId);
            return;
        }

        // rawBody와 PortOne 헤더를 SDK로 검증해서 위조 웹훅을 막는다
        io.portone.sdk.server.webhook.Webhook verified;
        try {
            verified = portOneWebhookHandler.verify(rawBody, webhookId, webhookSignature, webhookTimestamp);
        } catch (WebhookVerificationException e) {
            webhookService.fail(webhook);
            throw e;
        }

        PaymentCancelDecision result;
        try {
            result = processVerifiedWebhook(verified, webhook);
        } catch (RuntimeException e) {
            webhookService.fail(webhook);
            log.error("[PORTONE_WEBHOOK] business handling failed. id={}", webhookId, e);
            throw e;
        }

        // DB 기준으로 받을 수 없는 결제라면 DB 트랜잭션 밖에서 PortOne 결제를 취소
        if (result != null && result.cancelRequired()) {
            try {
                cancelGatewayPayment(result.pgKey(), result.cancelAmount(), result.cancelReason());
            } catch (RuntimeException e) {
                webhookService.fail(webhook);
                throw e;
            }
        }

        if (result == null || result.webhookCompletionRequired()) {
            webhookService.complete(webhook);
        }
    }

    private Payment createPaymentInOrderLock(
            UUID memberId,
            PaymentCreateRequest request,
            PaymentGatewayResponse gatewayResponse
    ) {
        if (request.memberCouponId() != null) {
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_COUPON_NOT_SUPPORTED);
        }

        Order order = getOrder(request.orderId());
        Course course = getCourse(order.getCourseId());

        // 결제 생성은 PENDING 주문, 본인 주문, 결제 기한 내 주문만 가능
        if (!order.getMemberId().equals(memberId)) {
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_FORBIDDEN);
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ServiceErrorException(OrderExceptionEnum.ERR_INVALID_ORDER_STATUS);
        }
        if (LocalDateTime.now().isAfter(order.getExpireAt())) {
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_DEADLINE_EXCEEDED);
        }
        if (!hasAvailableSeats(order, course)) {
            throw new ServiceErrorException(OrderExceptionEnum.ERR_NO_AVAILABLE_SEATS);
        }

        paymentService.validateNoPayment(order.getId());

        Long totalPrice = toLong(order.getTotalPriceSnap());
        PaymentCreateCommand preparation = new PaymentCreateCommand(
                order.getId(),
                memberId,
                null,
                totalPrice,
                NO_DISCOUNT,
                totalPrice
        );

        // 서버 계산 금액과 PortOne 실제 결제 금액이 일치해야 결제 요청을 저장
        paymentService.validateGatewayPayment(preparation, request.pgKey(), request.payWay(), gatewayResponse);

        // 웹훅 전에는 결제를 확정하지 않고 PENDING으로만 저장
        return paymentService.createPendingPayment(preparation, request.pgKey(), request.payWay());
    }

    private PaymentCancelDecision processVerifiedWebhook(
            io.portone.sdk.server.webhook.Webhook verified,
            Webhook webhook
    ) {
        PortOneWebhookEvent event = PortOneWebhookEvent.from(verified);

        if (!event.transaction()) {
            return transactionTemplate.execute(status -> {
                webhook.updateEventStatus(event.eventType());
                return PaymentCancelDecision.none();
            });
        }

        webhook.updateEventStatus(event.eventType());
        webhook.updatePgKey(event.pgKey());

        if ("WebhookTransactionPaid".equals(event.eventType())) {
            Optional<Payment> payment = waitForPayment(event.pgKey());
            if (payment.isEmpty()) {
                log.info("[PORTONE_WEBHOOK] payment is not saved yet. webhook deferred. pgKey={}", event.pgKey());
                return PaymentCancelDecision.defer();
            }

            UUID courseId = findCourseIdByPayment(payment.get());
            return paymentLockExecutor.executeWithPgKeyLock(event.pgKey(), () -> {
                return paymentLockExecutor.executeWithCourseLock(
                        courseId,
                        () -> transactionTemplate.execute(status -> processTransactionWebhook(event, webhook))
                );
            });
        }

        return paymentLockExecutor.executeWithPgKeyLock(
                event.pgKey(),
                () -> transactionTemplate.execute(status -> processTransactionWebhook(event, webhook))
        );
    }

    private PaymentCancelDecision processTransactionWebhook(PortOneWebhookEvent event, Webhook webhook) {
        webhook.updateEventStatus(event.eventType());
        webhook.updatePgKey(event.pgKey());

        log.info("[PORTONE_WEBHOOK] type={} pgKey={}", event.eventType(), event.pgKey());

        return switch (event.eventType()) {
            case "WebhookTransactionPaid" -> completePaidWebhook(event.pgKey());
            case "WebhookTransactionFailed", "WebhookTransactionCancelled" -> {
                Payment payment = paymentService.getByPgKeyForUpdate(event.pgKey());
                paymentService.fail(payment);
                yield PaymentCancelDecision.none();
            }
            default -> {
                log.warn("[PORTONE_WEBHOOK] unsupported event type. type={}", event.eventType());
                yield PaymentCancelDecision.none();
            }
        };
    }

    private PaymentCancelDecision completePaidWebhook(String pgKey) {

        // Payment row에 비관적 락
        Payment payment = paymentService.getByPgKeyForUpdate(pgKey);

        // 이미 PAID면 멱등 처리
        if (payment.isPaid()) {
            return PaymentCancelDecision.none();
        }

        if (!payment.isPending()) {
            return PaymentCancelDecision.cancel(payment.getPgKey(), payment.getPaidTotalPrice(), "PAYMENT_STATUS_INVALID");
        }

        Order order = getOrder(payment.getOrderId());
        Course course = getCourse(order.getCourseId());

        // 웹훅은 최종 확정 지점이므로 결제 생성 때 했던 검증 다시 수행
        if (!order.getMemberId().equals(payment.getMemberId())) {
            paymentService.fail(payment);
            return PaymentCancelDecision.cancel(payment.getPgKey(), payment.getPaidTotalPrice(), "PAYMENT_ORDER_MEMBER_MISMATCH");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            paymentService.fail(payment);
            return PaymentCancelDecision.cancel(payment.getPgKey(), payment.getPaidTotalPrice(), "ORDER_STATUS_INVALID");
        }
        if (LocalDateTime.now().isAfter(order.getExpireAt())) {
            paymentService.fail(payment);
            return PaymentCancelDecision.cancel(payment.getPgKey(), payment.getPaidTotalPrice(), "PAYMENT_DEADLINE_EXCEEDED");
        }
        if (!hasAvailableSeats(order, course)) {
            paymentService.fail(payment);
            return PaymentCancelDecision.cancel(payment.getPgKey(), payment.getPaidTotalPrice(), "NO_AVAILABLE_SEATS");
        }

        paymentService.confirmPaid(payment);
        increaseCourseConfirmCount(order, course);
        return PaymentCancelDecision.none();
    }

    private void completeAlreadyReceivedPaidWebhook(String pgKey) {
        Optional<Webhook> paidWebhook = webhookService.findProcessablePaidWebhook(pgKey);
        if (paidWebhook.isEmpty()) {
            return;
        }

        Webhook webhook = webhookService.retry(paidWebhook.get(), "WebhookTransactionPaid");
        log.info("[PORTONE_WEBHOOK] deferred paid webhook resumed. webhookId={} pgKey={}",
                webhook.getRecWebhookId(), pgKey);

        Payment payment = paymentService.getByPgKey(pgKey);
        UUID courseId = findCourseIdByPayment(payment);
        PaymentCancelDecision result = paymentLockExecutor.executeWithPgKeyLock(pgKey, () ->
                paymentLockExecutor.executeWithCourseLock(
                        courseId,
                        () -> transactionTemplate.execute(status -> processTransactionWebhook(
                                new PortOneWebhookEvent(true, "WebhookTransactionPaid", pgKey),
                                webhook
                        ))
                )
        );

        if (result != null && result.cancelRequired()) {
            try {
                cancelGatewayPayment(result.pgKey(), result.cancelAmount(), result.cancelReason());
            } catch (RuntimeException e) {
                webhookService.fail(webhook);
                throw e;
            }
        }

        if (result == null || result.webhookCompletionRequired()) {
            webhookService.complete(webhook);
        }
    }

    private UUID findCourseIdByPayment(Payment payment) {
        Order order = getOrder(payment.getOrderId());
        return order.getCourseId();
    }

    private Optional<Payment> waitForPayment(String pgKey) {
        for (int attempt = 1; attempt <= WEBHOOK_PAYMENT_LOOKUP_RETRY_COUNT; attempt++) {
            Optional<Payment> payment = paymentService.findByPgKeyInNewTransaction(pgKey);
            if (payment.isPresent()) {
                return payment;
            }

            if (attempt < WEBHOOK_PAYMENT_LOOKUP_RETRY_COUNT) {
                sleepBeforePaymentRetry(pgKey, attempt);
            }
        }

        return Optional.empty();
    }

    private void sleepBeforePaymentRetry(String pgKey, int attempt) {
        try {
            log.info("[PORTONE_WEBHOOK] payment not found yet. retry={} pgKey={}", attempt, pgKey);
            Thread.sleep(WEBHOOK_PAYMENT_LOOKUP_RETRY_INTERVAL_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_NOT_FOUND_PAYMENT);
        }
    }

    private boolean hasAvailableSeats(Order order, Course course) {
        return course.getConfirmCount() + order.getOrderCount() <= course.getCapacity();
    }

    private void increaseCourseConfirmCount(Order order, Course course) {
        for (int i = 0; i < order.getOrderCount(); i++) {
            course.increaseConfirmCount();
        }
    }

    private Order getOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ServiceErrorException(OrderExceptionEnum.ERR_NOT_FOUND_ORDER));
    }

    private Course getCourse(UUID courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_COURSE_NOT_FOUND));
    }

    private Long toLong(BigInteger value) {
        try {
            return value.longValueExact();
        } catch (ArithmeticException e) {
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_AMOUNT_MISMATCH);
        }
    }

    private void cancelGatewayPaymentSafely(String pgKey, Long amount, String reason) {
        try {
            cancelGatewayPayment(pgKey, amount, reason);
        } catch (RuntimeException e) {
            log.error("[PORTONE_PAYMENT] cancel request failed. pgKey={} amount={} reason={}",
                    pgKey, amount, reason, e);
        }
    }

    private void cancelGatewayPayment(String pgKey, Long amount, String reason) {
        paymentGateway.cancelPayment(PaymentGatewayRequest.of(pgKey, amount, reason));
    }

    private record PortOneWebhookEvent(boolean transaction, String eventType, String pgKey) {

        private static PortOneWebhookEvent from(io.portone.sdk.server.webhook.Webhook verified) {
            String eventType = verified.getClass().getSimpleName();

            if (!(verified instanceof WebhookTransaction transaction)) {
                return new PortOneWebhookEvent(false, eventType, null);
            }

            WebhookTransactionData data = transaction.getData();
            return new PortOneWebhookEvent(true, eventType, data.getPaymentId());
        }
    }
}
