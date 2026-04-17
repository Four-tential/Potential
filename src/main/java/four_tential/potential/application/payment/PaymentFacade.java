package four_tential.potential.application.payment;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.OrderExceptionEnum;
import four_tential.potential.common.exception.domain.PaymentExceptionEnum;
import four_tential.potential.application.payment.consts.PaymentWebhookConstants;
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

    private final PaymentService paymentService;
    private final WebhookService webhookService;
    private final PaymentGateway paymentGateway;
    private final PortOneWebhookHandler portOneWebhookHandler;
    private final TransactionTemplate transactionTemplate;
    private final OrderRepository orderRepository;
    private final CourseRepository courseRepository;
    private final PaymentDistributedLockExecutor paymentLockExecutor;

    /**
     * 결제 생성 API의 전체 흐름을 조율한다.
     * PortOne 실제 결제 정보와 서버 주문 정보를 검증한 뒤 PENDING 결제를 만들고,
     * 먼저 도착해 보류 중이던 Paid 웹훅이 있으면 즉시 이어서 확정 처리한다.
     */
    public PaymentCreateResponse createPayment(UUID memberId, PaymentCreateRequest request) {
        // PortOne 조회는 외부 API 호출이므로 분산락 밖에서 먼저 수행
        PaymentGatewayResponse gatewayResponse = paymentGateway.getPayment(request.pgKey());

        // 같은 주문으로 결제 생성 요청이 동시에 들어오는 것을 방지
        Payment payment = paymentLockExecutor.executeWithOrderLock(
                request.orderId(),
                () -> createPaymentInOrderLock(memberId, request, gatewayResponse)
        );

        // 결제 생성 API보다 Paid 웹훅이 먼저 도착해 PENDING으로 대기 중이면 즉시 확정 처리
        completeAlreadyReceivedPaidWebhook(payment.getPgKey());
        Payment latestPayment = paymentService.getByPgKey(payment.getPgKey());
        return PaymentCreateResponse.from(latestPayment);
    }

    /**
     * PortOne 웹훅 수신 API의 전체 흐름을 조율한다.
     * 수신 기록을 먼저 남긴 뒤 서명을 검증하고, 검증된 이벤트만 결제 상태 변경 로직으로 넘긴다.
     */
    public void handleWebhook(
            String rawBody,
            String webhookId,
            String webhookTimestamp,
            String webhookSignature) {

        // 이미 성공 처리된 webhook-id는 PortOne 재전송으로 보고 멱등하게 무시
        if (webhookService.isFinished(webhookId)) {
            log.info("[PORTONE_WEBHOOK] finished webhook ignored. id={}", webhookId);
            return;
        }

        // 수신 기록은 비즈니스 처리보다 먼저 남김
        Webhook webhook = webhookService.recordReceivedWebhook(
                webhookId,
                PaymentWebhookConstants.EVENT_STATUS_UNKNOWN,
                rawBody
        );

        if (webhook.isFinished()) {
            log.info("[PORTONE_WEBHOOK] finished webhook ignored after receive. id={}", webhookId);
            return;
        }

        // rawBody와 PortOne 헤더를 SDK로 검증해서 위조 웹훅을 막는다
        io.portone.sdk.server.webhook.Webhook verified;
        try {
            verified = portOneWebhookHandler.verify(rawBody, webhookId, webhookSignature, webhookTimestamp);
        } catch (WebhookVerificationException e) {
            log.warn("[PORTONE_WEBHOOK] 서명 검증 실패. id={} reason={}", webhookId, e.getMessage());
            webhookService.recordFailedWebhook(
                    webhook,
                    PaymentWebhookConstants.FAIL_REASON_WEBHOOK_SIGNATURE_INVALID,
                    e.getMessage()
            );
            return;
        }

        PaymentCancelDecision result;
        try {
            result = processVerifiedWebhook(verified, webhook);
        } catch (ServiceErrorException e) {
            webhookService.recordFailedWebhook(
                    webhook,
                    PaymentWebhookConstants.FAIL_REASON_WEBHOOK_BUSINESS_FAILED,
                    e.getMessage()
            );
            log.error("[PORTONE_WEBHOOK] business handling failed. id={} reason={}", webhookId, e.getMessage(), e);
            return;
        } catch (RuntimeException e) {
            webhookService.recordFailedWebhook(
                    webhook,
                    PaymentWebhookConstants.FAIL_REASON_WEBHOOK_UNEXPECTED_ERROR,
                    e.getMessage()
            );
            log.error("[PORTONE_WEBHOOK] business handling failed. id={}", webhookId, e);
            throw e;
        }

        // DB 기준으로 받을 수 없는 결제라면 DB 트랜잭션 밖에서 PortOne 결제를 취소
        if (result != null && result.cancelRequired()) {
            try {
                cancelGatewayPayment(result.pgKey(), result.cancelAmount(), result.cancelReason());
                webhookService.recordFailedWebhook(webhook, result.cancelReason(), "결제 확정 거절 후 PortOne 취소 완료");
                return;
            } catch (RuntimeException e) {
                webhookService.recordFailedWebhook(
                        webhook,
                        PaymentWebhookConstants.FAIL_REASON_PORTONE_CANCEL_FAILED,
                        e.getMessage()
                );
                log.error("[PORTONE_WEBHOOK] PortOne cancel failed. id={} pgKey={} reason={}",
                        webhookId, result.pgKey(), result.cancelReason(), e);
                return;
            }
        }

        if (result == null || result.webhookCompletionRequired()) {
            webhookService.recordCompletedWebhook(webhook);
        }
    }

    /**
     * 같은 주문에 대한 결제 생성 요청이 동시에 처리되지 않도록 주문 기준 분산락 안에서 실행된다.
     * 주문 소유자, 주문 상태, 결제 기한, 빈자리, 중복 결제, PortOne 금액을 검증한 뒤 PENDING 결제를 저장한다.
     */
    private Payment createPaymentInOrderLock(
            UUID memberId,
            PaymentCreateRequest request,
            PaymentGatewayResponse gatewayResponse
    ) {
        try {
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
        } catch (ServiceErrorException e) {
            // PortOne에서 실제로 결제가 완료된 경우에만 취소 요청
            // PAID가 아닌 상태(READY, FAILED 등)는 취소할 대상이 없다.
            if ("PAID".equals(gatewayResponse.status())) {
                cancelGatewayPaymentSafely(request.pgKey(), gatewayResponse.totalAmount(), "PAYMENT_CREATE_REJECTED");
            }
            webhookService.recordDeferredPaidWebhookFailure(
                    request.pgKey(),
                    PaymentWebhookConstants.FAIL_REASON_PAYMENT_CREATE_REJECTED,
                    e.getMessage()
            );
            throw e;
        }
    }

    /**
     * PortOne SDK가 검증한 웹훅 객체를 결제 도메인에서 처리할 수 있는 이벤트 정보로 변환한다.
     * Paid 웹훅이 payment 저장보다 먼저 도착한 경우에는 실패가 아니라 보류 상태로 남긴다.
     */
    private PaymentCancelDecision processVerifiedWebhook(
            io.portone.sdk.server.webhook.Webhook verified,
            Webhook webhook
    ) {
        PortOneWebhookEvent event = PortOneWebhookEvent.from(verified);

        if (!event.transaction()) {
            webhookService.updateEventStatus(webhook, event.eventType());
            return PaymentCancelDecision.none();
        }

        if (PaymentWebhookConstants.WEBHOOK_TRANSACTION_PAID.equals(event.eventType())) {
            Optional<Payment> payment = paymentService.findByPgKey(event.pgKey());
            if (payment.isEmpty()) {
                log.info("[PORTONE_WEBHOOK] payment is not saved yet. webhook deferred. pgKey={}", event.pgKey());
                webhookService.deferPaidWebhookUntilPaymentSaved(webhook, event.eventType(), event.pgKey());
                return PaymentCancelDecision.defer();
            }

            UUID courseId = findCourseIdByPayment(payment.get());
            return paymentLockExecutor.executeWithPgKeyLock(event.pgKey(), () ->
                    paymentLockExecutor.executeWithCourseLock(
                        courseId,
                        () -> transactionTemplate.execute(status -> processTransactionWebhook(event, webhook))
                    )
            );
        }

        return paymentLockExecutor.executeWithPgKeyLock(
                event.pgKey(),
                () -> transactionTemplate.execute(status -> processTransactionWebhook(event, webhook))
        );
    }

    /**
     * 실제 결제 트랜잭션 이벤트를 처리한다.
     * Paid 이벤트는 결제 확정으로, Failed/Cancelled 이벤트는 결제 실패로 반영한다.
     */
    private PaymentCancelDecision processTransactionWebhook(PortOneWebhookEvent event, Webhook webhook) {
        webhook.updateEventStatus(event.eventType());
        webhook.updatePgKey(event.pgKey());
        webhookService.merge(webhook);

        log.info("[PORTONE_WEBHOOK] type={} pgKey={}", event.eventType(), event.pgKey());

        switch (event.eventType()) {
            case PaymentWebhookConstants.WEBHOOK_TRANSACTION_PAID:
                return completePaidWebhook(event.pgKey());
            case PaymentWebhookConstants.WEBHOOK_TRANSACTION_FAILED,
                 PaymentWebhookConstants.WEBHOOK_TRANSACTION_CANCELLED:
                return failPaymentIfExists(event.pgKey());
            default:
                log.warn("[PORTONE_WEBHOOK] unsupported event type. type={}", event.eventType());
                return PaymentCancelDecision.none();
        }
    }

    /**
     * 실패/취소 웹훅을 처리한다.
     * 결제 생성 API가 호출되지 않은 실패 결제일 수 있으므로 payment row가 없으면 예외가 아니라 완료 처리한다.
     */
    private PaymentCancelDecision failPaymentIfExists(String pgKey) {
        Optional<Payment> payment = paymentService.findByPgKeyForUpdate(pgKey);
        if (payment.isEmpty()) {
            log.info("[PORTONE_WEBHOOK] payment not found for failed/cancelled event. webhook completed. pgKey={}", pgKey);
            return PaymentCancelDecision.none();
        }

        log.warn("[PORTONE_WEBHOOK] 결제 실패/취소 처리. pgKey={} memberId={}", pgKey, payment.get().getMemberId());
        paymentService.fail(payment.get());
        return PaymentCancelDecision.none();
    }

    /**
     * Paid 웹훅을 최종 결제 확정으로 처리한다.
     * payment row를 비관적 락으로 잡은 뒤 주문과 코스 상태를 다시 검증하고, 문제가 없을 때만 PAID로 변경한다.
     */
    private PaymentCancelDecision completePaidWebhook(String pgKey) {

        // Payment row에 비관적 락
        Payment payment = paymentService.getByPgKeyForUpdate(pgKey);

        if (payment.isPaid()) {
            log.info("[PORTONE_WEBHOOK] 이미 PAID 상태 — 멱등 처리. pgKey={}", pgKey);
            return PaymentCancelDecision.none();
        }

        if (!payment.isPending()) {
            log.warn("[PORTONE_WEBHOOK] PENDING이 아닌 결제에 Paid 웹훅 수신. pgKey={} status={}",
                    pgKey, payment.getStatus());
            return PaymentCancelDecision.cancel(payment.getPgKey(), payment.getPaidTotalPrice(), "PAYMENT_STATUS_INVALID");
        }

        Order order = getOrder(payment.getOrderId());
        Course course = getCourse(order.getCourseId());

        // 웹훅은 최종 확정 지점이므로 결제 생성 때 했던 검증 다시 수행
        if (!order.getMemberId().equals(payment.getMemberId())) {
            log.error("[PORTONE_WEBHOOK] 주문 회원 불일치. pgKey={}", pgKey);
            paymentService.fail(payment);
            return PaymentCancelDecision.cancel(payment.getPgKey(), payment.getPaidTotalPrice(), "PAYMENT_ORDER_MEMBER_MISMATCH");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            log.warn("[PORTONE_WEBHOOK] 주문 상태 불일치. pgKey={} orderStatus={}", pgKey, order.getStatus());
            paymentService.fail(payment);
            return PaymentCancelDecision.cancel(payment.getPgKey(), payment.getPaidTotalPrice(), "ORDER_STATUS_INVALID");
        }
        if (LocalDateTime.now().isAfter(order.getExpireAt())) {
            log.warn("[PORTONE_WEBHOOK] 결제 기한 초과. pgKey={}", pgKey);
            paymentService.fail(payment);
            return PaymentCancelDecision.cancel(payment.getPgKey(), payment.getPaidTotalPrice(), "PAYMENT_DEADLINE_EXCEEDED");
        }
        if (!hasAvailableSeats(order, course)) {
            log.warn("[PORTONE_WEBHOOK] 잔여 자리 없음. pgKey={}", pgKey);
            paymentService.fail(payment);
            return PaymentCancelDecision.cancel(payment.getPgKey(), payment.getPaidTotalPrice(), "NO_AVAILABLE_SEATS");
        }

        paymentService.confirmPaid(payment);
        increaseCourseConfirmCount(order, course);
        log.info("[PORTONE_WEBHOOK] 결제 확정 완료. pgKey={} memberId={}", pgKey, payment.getMemberId());
        return PaymentCancelDecision.none();
    }

    /**
     * 결제 생성 API보다 먼저 도착해 PENDING으로 보류된 Paid 웹훅을 찾아 재처리한다.
     * 결제 생성 직후 호출되어, 사용자가 웹훅 재발송을 누르지 않아도 결제 확정 흐름이 이어지게 한다.
     */
    private void completeAlreadyReceivedPaidWebhook(String pgKey) {
        Optional<Webhook> paidWebhook = webhookService.findProcessablePaidWebhook(pgKey);
        if (paidWebhook.isEmpty()) {
            return;
        }

        Webhook webhook = webhookService.markWebhookPendingForRetry(
                paidWebhook.get(),
                PaymentWebhookConstants.WEBHOOK_TRANSACTION_PAID
        );
        log.info("[PORTONE_WEBHOOK] deferred paid webhook resumed. webhookId={} pgKey={}",
                webhook.getRecWebhookId(), pgKey);

        Payment payment = paymentService.getByPgKey(pgKey);
        UUID courseId = findCourseIdByPayment(payment);
        PaymentCancelDecision result = paymentLockExecutor.executeWithPgKeyLock(pgKey, () ->
                paymentLockExecutor.executeWithCourseLock(
                        courseId,
                        () -> transactionTemplate.execute(status -> processTransactionWebhook(
                                new PortOneWebhookEvent(true, PaymentWebhookConstants.WEBHOOK_TRANSACTION_PAID, pgKey),
                                webhook
                        ))
                )
        );

        if (result != null && result.cancelRequired()) {
            try {
                cancelGatewayPayment(result.pgKey(), result.cancelAmount(), result.cancelReason());
                webhookService.recordFailedWebhook(webhook, result.cancelReason(), "결제 확정 거절 후 PortOne 취소 완료");
                return;
            } catch (RuntimeException e) {
                webhookService.recordFailedWebhook(
                        webhook,
                        PaymentWebhookConstants.FAIL_REASON_PORTONE_CANCEL_FAILED,
                        e.getMessage()
                );
                log.error("[PORTONE_WEBHOOK] PortOne cancel failed. webhookId={} pgKey={} reason={}",
                        webhook.getRecWebhookId(), result.pgKey(), result.cancelReason(), e);
                return;
            }
        }

        if (result == null || result.webhookCompletionRequired()) {
            webhookService.recordCompletedWebhook(webhook);
        }
    }

    /**
     * 결제에 연결된 주문을 조회해 해당 주문의 courseId를 찾는다.
     * 코스 기준 분산락 키를 만들기 위해 사용한다.
     */
    private UUID findCourseIdByPayment(Payment payment) {
        Order order = getOrder(payment.getOrderId());
        return order.getCourseId();
    }

    /**
     * 현재 확정 인원에 이번 주문 수량을 더해도 코스 정원을 넘지 않는지 확인한다.
     * 결제 생성 시점과 웹훅 확정 시점 모두에서 빈자리 검증에 사용한다.
     */
    private boolean hasAvailableSeats(Order order, Course course) {
        return course.getConfirmCount() + order.getOrderCount() <= course.getCapacity();
    }

    /**
     * 결제가 확정된 주문 수량만큼 코스 확정 인원을 증가시킨다.
     * 주문 수량이 여러 장일 수 있으므로 orderCount만큼 반복해서 증가시킨다.
     */
    private void increaseCourseConfirmCount(Order order, Course course) {
        for (int i = 0; i < order.getOrderCount(); i++) {
            course.increaseConfirmCount();
        }
    }

    /**
     * 주문 ID로 주문을 조회한다.
     * 결제 파트에서는 주문 도메인 코드를 직접 수정하지 않고, 조회 결과를 검증에만 사용한다.
     */
    private Order getOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ServiceErrorException(OrderExceptionEnum.ERR_NOT_FOUND_ORDER));
    }

    /**
     * 코스 ID로 코스를 조회한다.
     * 결제 확정 가능 여부와 confirmCount 증가 처리를 위해 사용한다.
     */
    private Course getCourse(UUID courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_COURSE_NOT_FOUND));
    }

    /**
     * 주문 금액 스냅샷 BigInteger를 결제 도메인에서 사용하는 Long 금액으로 변환한다.
     * Long 범위를 넘는 비정상 값은 금액 불일치로 보고 결제 생성을 거절한다.
     */
    private Long toLong(BigInteger value) {
        try {
            return value.longValueExact();
        } catch (ArithmeticException e) {
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_AMOUNT_MISMATCH);
        }
    }

    /**
     * 결제 생성 검증 실패 시 PortOne 취소를 시도하되, 취소 요청 실패가 원래 예외를 덮지 않도록 로그만 남긴다.
     * 서버 검증은 이미 실패했으므로 운영자가 확인할 수 있게 pgKey와 금액을 함께 기록한다.
     */
    private void cancelGatewayPaymentSafely(String pgKey, Long amount, String reason) {
        try {
            cancelGatewayPayment(pgKey, amount, reason);
        } catch (RuntimeException e) {
            log.error("[PORTONE_PAYMENT] cancel request failed. pgKey={} amount={} reason={}",
                    pgKey, amount, reason, e);
        }
    }

    /**
     * PortOne 결제 취소 API를 호출한다.
     * DB 트랜잭션 안에서 외부 API를 직접 호출하지 않도록 별도 메서드로 분리해 사용한다.
     */
    private void cancelGatewayPayment(String pgKey, Long amount, String reason) {
        paymentGateway.cancelPayment(PaymentGatewayRequest.of(pgKey, amount, reason));
    }

    private record PortOneWebhookEvent(boolean transaction, String eventType, String pgKey) {

        /**
         * PortOne SDK 웹훅 객체에서 결제 도메인 처리에 필요한 이벤트 타입과 pgKey를 뽑아낸다.
         * 결제 트랜잭션 웹훅이 아닌 이벤트는 pgKey 없이 별도 처리한다.
         */
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
