package four_tential.potential.application.payment;

import four_tential.potential.application.order.OrderService;
import four_tential.potential.application.order.WaitingListService;
import four_tential.potential.application.payment.consts.PaymentWebhookConstants;
import four_tential.potential.common.dto.PageResponse;
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
import four_tential.potential.domain.payment.enums.PaymentStatus;
import four_tential.potential.domain.payment.port.PaymentGateway;
import four_tential.potential.domain.payment.port.PaymentGatewayRequest;
import four_tential.potential.domain.payment.port.PaymentGatewayResponse;
import four_tential.potential.presentation.payment.dto.PaymentCreateRequest;
import four_tential.potential.presentation.payment.dto.PaymentCreateResponse;
import four_tential.potential.presentation.payment.dto.PaymentDetailResponse;
import four_tential.potential.presentation.payment.dto.PaymentListResponse;
import io.portone.sdk.server.webhook.WebhookTransaction;
import io.portone.sdk.server.webhook.WebhookTransactionData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
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

    private static final String PAYMENT_PG_KEY_PREFIX = "p";
    private static final int PAYMENT_PG_KEY_GENERATION_MAX_RETRY = 5;

    private final PaymentService paymentService;
    private final WebhookService webhookService;
    private final PaymentGateway paymentGateway;
    private final TransactionTemplate transactionTemplate;
    private final OrderRepository orderRepository;
    private final CourseRepository courseRepository;
    private final PaymentDistributedLockExecutor paymentLockExecutor;
    private final OrderService orderService;
    private final WaitingListService waitingListService;

    /**
     * 결제 준비 요청을 처리한다.
     * 서버가 pgKey를 먼저 만들고 payments row를 PENDING으로 저장한 뒤,
     * 프론트는 응답으로 받은 pgKey로 PortOne 결제창을 연다.
     */
    public PaymentCreateResponse createPayment(UUID memberId, PaymentCreateRequest request) {
        Payment payment = paymentLockExecutor.executeWithOrderLock(
                request.orderId(),
                () -> createPaymentInOrderLock(memberId, request)
        );

        return PaymentCreateResponse.from(payment);
    }

    /**
     * 결제 단건 조회
     */
    public PaymentDetailResponse getMyPayment(UUID memberId, UUID paymentId) {
        return paymentService.getMyPayment(paymentId, memberId);
    }

    /**
     * 결제 목록 조회
     */
    public PageResponse<PaymentListResponse> getAllMyPayments(UUID memberId, PaymentStatus status, Pageable pageable) {
        return PageResponse.register(paymentService.getAllMyPayments(memberId, status, pageable));
    }

    /**
     * PortOne 웹훅을 처리한다.
     * WebhookController에서 서명 검증을 마친 webhook만 전달받아 결제 상태를 반영한다.
     */
    public void handleWebhook(
            String rawBody,
            String webhookId,
            io.portone.sdk.server.webhook.Webhook verified
    ) {
        Optional<Webhook> receivedWebhook = receiveWebhook(rawBody, webhookId);
        if (receivedWebhook.isEmpty()) {
            return;
        }

        Webhook webhook = receivedWebhook.get();

        PaymentCancelDecision result;
        try {
            result = handleVerifiedWebhook(verified, webhook);
        } catch (ServiceErrorException e) {
            webhookService.failWebhook(
                    webhook,
                    PaymentWebhookConstants.FAIL_REASON_WEBHOOK_BUSINESS_FAILED,
                    e.getMessage()
            );
            log.error("[PORTONE_WEBHOOK] business handling failed. id={} reason={}", webhookId, e.getMessage(), e);
            return;
        } catch (RuntimeException e) {
            webhookService.failWebhook(
                    webhook,
                    PaymentWebhookConstants.FAIL_REASON_WEBHOOK_UNEXPECTED_ERROR,
                    e.getMessage()
            );
            log.error("[PORTONE_WEBHOOK] business handling failed. id={}", webhookId, e);
            throw e;
        }

        if (result != null && result.cancelRequired()) {
            try {
                cancelGatewayPayment(result.pgKey(), result.cancelAmount(), result.cancelReason());
                webhookService.failWebhook(
                        webhook,
                        result.cancelReason(),
                        "결제 확정 불가로 내부 상태를 실패로 반영하고 PortOne 취소까지 완료"
                );
                return;
            } catch (RuntimeException e) {
                webhookService.failWebhook(
                        webhook,
                        PaymentWebhookConstants.FAIL_REASON_PORTONE_CANCEL_FAILED,
                        e.getMessage()
                );
                log.error(
                        "[PORTONE_WEBHOOK] PortOne cancel failed. id={} pgKey={} reason={}",
                        webhookId,
                        result.pgKey(),
                        result.cancelReason(),
                        e
                );
                return;
            }
        }

        if (result == null || result.webhookCompletionRequired()) {
            webhookService.completeWebhook(webhook);
        }
    }

    /**
     * 서명 검증에 실패한 웹훅을 실패 이력으로 남긴다.
     */
    public void handleInvalidWebhook(String rawBody, String webhookId, String failMessage) {
        Optional<Webhook> receivedWebhook = receiveWebhook(rawBody, webhookId);
        if (receivedWebhook.isEmpty()) {
            return;
        }

        webhookService.failWebhook(
                receivedWebhook.get(),
                PaymentWebhookConstants.FAIL_REASON_WEBHOOK_SIGNATURE_INVALID,
                failMessage
        );
    }

    private Optional<Webhook> receiveWebhook(String rawBody, String webhookId) {
        if (webhookService.isFinished(webhookId)) {
            log.info("[PORTONE_WEBHOOK] finished webhook ignored. id={}", webhookId);
            return Optional.empty();
        }

        Webhook webhook = webhookService.saveIncomingWebhook(
                webhookId,
                PaymentWebhookConstants.EVENT_STATUS_UNKNOWN,
                rawBody
        );

        if (webhook.isFinished()) {
            log.info("[PORTONE_WEBHOOK] finished webhook ignored after receive. id={}", webhookId);
            return Optional.empty();
        }

        return Optional.of(webhook);
    }

    /**
     * 주문 락 안에서 결제 준비를 완료한다.
     * 이 단계에서 pgKey를 서버가 만들고 payments row를 먼저 저장한다.
     */
    private Payment createPaymentInOrderLock(UUID memberId, PaymentCreateRequest request) {
        Order order = getOrder(request.orderId());

        if (!order.getMemberId().equals(memberId)) {
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_FORBIDDEN);
        }

        Optional<Payment> existingPayment = paymentService.findByOrderId(order.getId());
        if (existingPayment.isPresent()) {
            return getExistingPaymentOrReject(existingPayment.get());
        }

        Course course = getCourse(order.getCourseId());
        validateOrderForPayment(order, course);
        paymentService.validateNoPayment(order.getId());

        Long totalPrice = toLong(order.getTotalPriceSnap());
        PaymentCreateCommand preparation = new PaymentCreateCommand(
                order.getId(),
                memberId,
                totalPrice,
                totalPrice
        );

        String pgKey = generatePgKey();
        return paymentService.createPendingPayment(preparation, pgKey, request.payWay());
    }

    /**
     * 같은 주문에 대해 이미 준비된 결제가 있으면 그대로 재사용하고,
     * 이미 끝난 결제라면 새 결제를 만들지 않는다.
     */
    private Payment getExistingPaymentOrReject(Payment existingPayment) {
        if (existingPayment.isPending()) {
            log.info(
                    "[PORTONE_PAYMENT] duplicate payment prepare request ignored. orderId={} pgKey={}",
                    existingPayment.getOrderId(),
                    existingPayment.getPgKey()
            );
            return existingPayment;
        }

        if (existingPayment.isPaid()) {
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_ALREADY_PAID);
        }

        throw new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_ALREADY_REQUESTED);
    }

    /**
     * 결제창을 열기 전에 주문이 아직 결제 가능한 상태인지 확인한다.
     */
    private void validateOrderForPayment(Order order, Course course) {
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ServiceErrorException(OrderExceptionEnum.ERR_INVALID_ORDER_STATUS);
        }
        if (LocalDateTime.now().isAfter(order.getExpireAt())) {
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_DEADLINE_EXCEEDED);
        }
        if (!hasAvailableSeats(order, course)) {
            throw new ServiceErrorException(OrderExceptionEnum.ERR_NO_AVAILABLE_SEATS);
        }
    }

    /**
     * 프론트가 임의로 만들지 않도록 서버에서 PortOne paymentId(pgKey)를 생성한다.
     * 카드 결제 테스트 페이지와 PortOne 브라우저 SDK를 함께 쓰기 위해 40자 이내로 맞춘다.
     */
    private String generatePgKey() {
        for (int attempt = 0; attempt < PAYMENT_PG_KEY_GENERATION_MAX_RETRY; attempt++) {
            String pgKey = PAYMENT_PG_KEY_PREFIX + UUID.randomUUID().toString().replace("-", "");
            if (paymentService.findByPgKey(pgKey).isEmpty()) {
                return pgKey;
            }
        }

        throw new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_KEY_GENERATION_FAILED);
    }

    /**
     * 서명 검증이 끝난 웹훅을 결제 이벤트로 해석한다.
     */
    private PaymentCancelDecision handleVerifiedWebhook(
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
                webhook.updateEventStatus(event.eventType());
                webhook.updatePgKey(event.pgKey());
                log.error("[PORTONE_WEBHOOK] payment not found for paid event. cancel required. pgKey={}", event.pgKey());

                PaymentGatewayResponse gatewayResponse = paymentGateway.getPayment(event.pgKey());
                return PaymentCancelDecision.cancel(
                        event.pgKey(),
                        gatewayResponse.totalAmount(),
                        "PAYMENT_NOT_FOUND"
                );
            }

            UUID courseId = findCourseIdByPayment(payment.get());
            return paymentLockExecutor.executeWithPgKeyLock(event.pgKey(), () ->
                    paymentLockExecutor.executeWithCourseLock(
                            courseId,
                            () -> transactionTemplate.execute(status -> handleTransactionWebhook(event, webhook))
                    )
            );
        }

        return paymentLockExecutor.executeWithPgKeyLock(
                event.pgKey(),
                () -> transactionTemplate.execute(status -> handleTransactionWebhook(event, webhook))
        );
    }

    /**
     * PortOne 결제 이벤트를 실제 Payment 상태 변경으로 연결한다.
     */
    private PaymentCancelDecision handleTransactionWebhook(PortOneWebhookEvent event, Webhook webhook) {
        webhook.updateEventStatus(event.eventType());
        webhook.updatePgKey(event.pgKey());
        webhookService.merge(webhook);

        log.info("[PORTONE_WEBHOOK] type={} pgKey={}", event.eventType(), event.pgKey());

        switch (event.eventType()) {
            case PaymentWebhookConstants.WEBHOOK_TRANSACTION_PAID:
                return confirmPaidWebhook(event.pgKey());

            case PaymentWebhookConstants.WEBHOOK_TRANSACTION_FAILED,
                    PaymentWebhookConstants.WEBHOOK_TRANSACTION_CANCELLED:
                return failExistingPayment(event.pgKey());

            case PaymentWebhookConstants.WEBHOOK_TRANSACTION_CANCELLED_CANCELLED,
                    PaymentWebhookConstants.WEBHOOK_TRANSACTION_CANCELLED_PARTIAL_CANCELLED:
                log.info("[PORTONE_WEBHOOK] refund completed event received. type={} pgKey={}", event.eventType(), event.pgKey());
                return PaymentCancelDecision.none();

            default:
                log.warn("[PORTONE_WEBHOOK] unsupported event type. type={}", event.eventType());
                return PaymentCancelDecision.none();
        }
    }

    /**
     * Failed 또는 Cancelled 웹훅을 기존 Payment에 반영한다.
     */
    private PaymentCancelDecision failExistingPayment(String pgKey) {
        Optional<Payment> payment = paymentService.findByPgKeyForUpdate(pgKey);
        if (payment.isEmpty()) {
            log.info("[PORTONE_WEBHOOK] payment not found for failed/cancelled event. webhook completed. pgKey={}", pgKey);
            return PaymentCancelDecision.none();
        }

        log.warn("[PORTONE_WEBHOOK] payment failed/cancelled. pgKey={} memberId={}", pgKey, payment.get().getMemberId());
        paymentService.fail(payment.get());
        return PaymentCancelDecision.none();
    }

    /**
     * Paid 웹훅으로 결제를 최종 확정한다.
     * 결제창을 열기 전 검증과 별개로, 확정 직전에도 주문 상태와 좌석을 다시 확인한다.
     */
    private PaymentCancelDecision confirmPaidWebhook(String pgKey) {
        Payment payment = paymentService.getByPgKeyForUpdate(pgKey);

        if (payment.isPaid()) {
            log.info("[PORTONE_WEBHOOK] already PAID. idempotent handling. pgKey={}", pgKey);
            return PaymentCancelDecision.none();
        }

        if (!payment.isPending()) {
            log.warn(
                    "[PORTONE_WEBHOOK] paid webhook received for non-pending payment. pgKey={} status={}",
                    pgKey,
                    payment.getStatus()
            );
            return PaymentCancelDecision.cancel(payment.getPgKey(), payment.getPaidTotalPrice(), "PAYMENT_STATUS_INVALID");
        }

        Order order = getOrder(payment.getOrderId());
        Course course = getCourse(order.getCourseId());

        if (!order.getMemberId().equals(payment.getMemberId())) {
            log.error("[PORTONE_WEBHOOK] order member mismatch. pgKey={}", pgKey);
            paymentService.fail(payment);
            return PaymentCancelDecision.cancel(payment.getPgKey(), payment.getPaidTotalPrice(), "PAYMENT_ORDER_MEMBER_MISMATCH");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            log.warn("[PORTONE_WEBHOOK] invalid order status. pgKey={} orderStatus={}", pgKey, order.getStatus());
            paymentService.fail(payment);
            return PaymentCancelDecision.cancel(payment.getPgKey(), payment.getPaidTotalPrice(), "ORDER_STATUS_INVALID");
        }
        if (LocalDateTime.now().isAfter(order.getExpireAt())) {
            log.warn("[PORTONE_WEBHOOK] payment deadline exceeded. pgKey={}", pgKey);
            paymentService.fail(payment);
            return PaymentCancelDecision.cancel(payment.getPgKey(), payment.getPaidTotalPrice(), "PAYMENT_DEADLINE_EXCEEDED");
        }
        if (!hasAvailableSeats(order, course)) {
            log.warn("[PORTONE_WEBHOOK] no available seats. pgKey={}", pgKey);
            paymentService.fail(payment);
            return PaymentCancelDecision.cancel(payment.getPgKey(), payment.getPaidTotalPrice(), "NO_AVAILABLE_SEATS");
        }

        paymentService.confirmPaid(payment);
        orderService.completePayment(order.getId());
        increaseCourseConfirmCount(order, course);
        completeOccupyingSeatQuietly(order);

        log.info("[PORTONE_WEBHOOK] payment confirmed. pgKey={} memberId={}", pgKey, payment.getMemberId());
        return PaymentCancelDecision.none();
    }

    /**
     * 코스 기준 분산락을 걸기 위해 payment에서 courseId를 찾는다.
     */
    private UUID findCourseIdByPayment(Payment payment) {
        Order order = getOrder(payment.getOrderId());
        return order.getCourseId();
    }

    /**
     * 이번 주문 수량까지 포함해도 정원을 넘지 않는지 확인한다.
     */
    private boolean hasAvailableSeats(Order order, Course course) {
        return course.getConfirmCount() + order.getOrderCount() <= course.getCapacity();
    }

    /**
     * 결제 수량만큼 코스 확정 인원을 늘린다.
     */
    private void increaseCourseConfirmCount(Order order, Course course) {
        for (int i = 0; i < order.getOrderCount(); i++) {
            course.increaseConfirmCount();
        }
    }

    /**
     * 결제 검증에 필요한 주문을 조회한다.
     */
    private Order getOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ServiceErrorException(OrderExceptionEnum.ERR_NOT_FOUND_ORDER));
    }

    /**
     * 좌석 검증과 확정 인원 증가에 필요한 코스를 조회한다.
     */
    private Course getCourse(UUID courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_COURSE_NOT_FOUND));
    }

    /**
     * 주문 금액 스냅샷을 결제 금액 타입으로 바꾼다.
     */
    private Long toLong(BigInteger value) {
        try {
            return value.longValueExact();
        } catch (ArithmeticException e) {
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_AMOUNT_MISMATCH);
        }
    }

    /**
     * PortOne 결제 취소 API를 호출한다.
     */
    private void cancelGatewayPayment(String pgKey, Long amount, String reason) {
        log.warn("[PORTONE_PAYMENT] cancel request. pgKey={} amount={} reason={}", pgKey, amount, reason);
        paymentGateway.cancelPayment(PaymentGatewayRequest.of(pgKey, amount, reason));
    }

    /**
     * Redis 점유 정리는 실패해도 결제 확정은 유지한다.
     */
    private void completeOccupyingSeatQuietly(Order order) {
        try {
            waitingListService.completeOccupyingSeat(order.getCourseId(), order.getMemberId());
        } catch (RuntimeException e) {
            log.error(
                    "[PORTONE_WEBHOOK] Redis occupancy finalize failed. orderId={} courseId={} memberId={}",
                    order.getId(),
                    order.getCourseId(),
                    order.getMemberId(),
                    e
            );
        }
    }

    private record PortOneWebhookEvent(boolean transaction, String eventType, String pgKey) {

        /**
         * PortOne 웹훅에서 결제 처리에 필요한 타입과 pgKey만 꺼낸다.
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
