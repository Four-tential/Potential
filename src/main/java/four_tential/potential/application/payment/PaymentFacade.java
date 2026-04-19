package four_tential.potential.application.payment;

import four_tential.potential.application.order.OrderService;
import four_tential.potential.application.order.WaitingListService;
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
import four_tential.potential.domain.payment.enums.PaymentPayWay;
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
    private final OrderService orderService;
    private final WaitingListService waitingListService;

    /**
     * 결제 생성 요청을 처리한다
     * PortOne 결제 결과와 우리 주문 정보를 다시 맞춰 보고, 문제가 없으면 PENDING 결제를 저장한다
     * 먼저 와서 기다리던 Paid 웹훅이 있으면 이어서 PAID 확정까지 진행한다
     */
    public PaymentCreateResponse createPayment(UUID memberId, PaymentCreateRequest request) {
        // 외부 API는 락을 잡기 전에 호출해 주문 락 시간을 짧게 둔다
        PaymentGatewayResponse gatewayResponse = paymentGateway.getPayment(request.pgKey());

        // 같은 주문의 중복 결제 생성을 막기 위해 주문 기준으로 묶는다
        Payment payment = paymentLockExecutor.executeWithOrderLock(
                request.orderId(),
                () -> createPaymentInOrderLock(memberId, request, gatewayResponse)
        );

        // 기다리던 Paid 웹훅이 있으면 결제 생성 직후 바로 이어 처리한다
        resumePendingPaidWebhook(payment.getPgKey());
        Payment latestPayment = paymentService.getByPgKey(payment.getPgKey());
        return PaymentCreateResponse.from(latestPayment);
    }

    /**
     * PortOne 웹훅을 처리한다
     * 원본 요청을 먼저 저장한 뒤 서명을 검증하고, 검증된 이벤트만 결제 상태에 반영한다
     * 다시 받아도 결과가 달라지지 않는 실패는 사유를 남기고 조용히 종료한다
     */
    public void handleWebhook(
            String rawBody,
            String webhookId,
            String webhookTimestamp,
            String webhookSignature) {

        // 이미 끝난 webhook-id면 재전송으로 보고 더 처리하지 않는다
        if (webhookService.isFinished(webhookId)) {
            log.info("[PORTONE_WEBHOOK] finished webhook ignored. id={}", webhookId);
            return;
        }

        // 처리 중 실패해도 추적할 수 있게 원본 payload부터 저장한다
        Webhook webhook = webhookService.saveIncomingWebhook(
                webhookId,
                PaymentWebhookConstants.EVENT_STATUS_UNKNOWN,
                rawBody
        );

        if (webhook.isFinished()) {
            log.info("[PORTONE_WEBHOOK] finished webhook ignored after receive. id={}", webhookId);
            return;
        }

        // 서명이 맞는 웹훅만 결제 이벤트로 믿고 처리한다
        io.portone.sdk.server.webhook.Webhook verified;
        try {
            verified = portOneWebhookHandler.verify(rawBody, webhookId, webhookSignature, webhookTimestamp);
        } catch (WebhookVerificationException e) {
            log.warn("[PORTONE_WEBHOOK] 서명 검증 실패. id={} reason={}", webhookId, e.getMessage());
            webhookService.failWebhook(
                    webhook,
                    PaymentWebhookConstants.FAIL_REASON_WEBHOOK_SIGNATURE_INVALID,
                    e.getMessage()
            );
            return;
        }

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

        // 돈은 나갔지만 우리 규칙상 받을 수 없는 결제라면, DB 처리 뒤 PortOne 취소를 시도한다
        if (result != null && result.cancelRequired()) {
            try {
                cancelGatewayPayment(result.pgKey(), result.cancelAmount(), result.cancelReason());
                webhookService.failWebhook(webhook, result.cancelReason(), "결제 확정 거절 후 PortOne 취소 완료");
                return;
            } catch (RuntimeException e) {
                webhookService.failWebhook(
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
            webhookService.completeWebhook(webhook);
        }
    }

    /**
     * 주문 락 안에서 결제를 만들 수 있는지 확인한다
     * 본인 주문인지, 기한과 자리가 괜찮은지, PortOne 금액이 맞는지 본 뒤 PENDING 또는 FAILED로 저장한다
     */
    private Payment createPaymentInOrderLock(
            UUID memberId,
            PaymentCreateRequest request,
            PaymentGatewayResponse gatewayResponse
    ) {
        PaymentCreateCommand failedPaymentCommand = null;
        try {
            if (request.memberCouponId() != null) {
                throw new ServiceErrorException(PaymentExceptionEnum.ERR_COUPON_NOT_SUPPORTED);
            }

            Order order = getOrder(request.orderId());
            // 타인의 주문이면 결제 기록도 남기지 않는다
            if (!order.getMemberId().equals(memberId)) {
                throw new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_FORBIDDEN);
            }

            Optional<Payment> existingPayment = paymentService.findByOrderId(order.getId());
            if (existingPayment.isPresent()) {
                return getExistingPaymentOrReject(request, existingPayment.get());
            }

            // 여기부터는 실패하더라도 "결제 시도"로 남길 수 있다
            Long totalPrice = toLong(order.getTotalPriceSnap());
            failedPaymentCommand = new PaymentCreateCommand(
                    order.getId(),
                    memberId,
                    null,
                    totalPrice,
                    NO_DISCOUNT,
                    totalPrice
            );

            Course course = getCourse(order.getCourseId());

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

            if (isGatewayNotPaid(gatewayResponse)) {
                validateFailedGatewayResult(request.pgKey(), request.payWay(), gatewayResponse);
                return paymentService.createFailedPayment(failedPaymentCommand, request.pgKey(), request.payWay());
            }

            // 성공 결제는 서버 금액과 PortOne 금액이 정확히 같아야 한다
            paymentService.validateGatewayPayment(failedPaymentCommand, request.pgKey(), request.payWay(), gatewayResponse);

            // 결제 확정은 서명 검증된 Paid 웹훅에서 한다
            return paymentService.createPendingPayment(failedPaymentCommand, request.pgKey(), request.payWay());
        } catch (ServiceErrorException e) {
            // PortOne에서는 결제됐지만 우리 서버가 거절한 경우, 취소를 시도하고 FAILED 기록을 남긴다
            if (cancelRejectedPayment(request, gatewayResponse)) {
                saveRejectedPayment(failedPaymentCommand, request);
                webhookService.failDeferredPaidWebhook(
                        request.pgKey(),
                        PaymentWebhookConstants.FAIL_REASON_PAYMENT_CREATE_REJECTED,
                        e.getMessage()
                );
            }
            throw e;
        }
    }

    private Payment getExistingPaymentOrReject(PaymentCreateRequest request, Payment existingPayment) {
        if (request.pgKey().equals(existingPayment.getPgKey())) {
            log.info("[PORTONE_PAYMENT] duplicate payment create request ignored. orderId={} pgKey={}",
                    request.orderId(), request.pgKey());
            return existingPayment;
        }

        throw new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_ALREADY_REQUESTED);
    }

    /**
     * PortOne 상태가 PAID가 아니면 성공 결제가 아니라 실패 시도로 다룬다
     */
    private boolean isGatewayNotPaid(PaymentGatewayResponse gatewayResponse) {
        return !"PAID".equals(gatewayResponse.status());
    }

    /**
     * 실패 결제도 같은 pgKey와 허용된 결제 수단일 때만 기록한다
     */
    private void validateFailedGatewayResult(
            String pgKey,
            PaymentPayWay requestPayWay,
            PaymentGatewayResponse gatewayResponse
    ) {
        if (requestPayWay != PaymentPayWay.CARD) {
            log.warn("[PAYMENT_VALIDATE] unsupported payment method for failed payment. requestPayWay={}", requestPayWay);
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_METHOD_NOT_ALLOWED);
        }
        if (!pgKey.equals(gatewayResponse.pgKey())) {
            log.error("[PAYMENT_VALIDATE] failed payment pgKey mismatch. request={} gateway={}",
                    pgKey, gatewayResponse.pgKey());
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_KEY_MISMATCH);
        }

        log.warn("[PORTONE_PAYMENT] failed payment recorded. pgKey={} status={} amount={}",
                pgKey, gatewayResponse.status(), gatewayResponse.totalAmount());
    }

    /**
     * PortOne은 결제됐지만 우리 서버가 거절한 건을 FAILED로 남긴다
     * 이미 같은 주문의 결제가 있거나 저장에 실패해도 원래 거절 흐름은 막지 않는다
     */
    private void saveRejectedPayment(
            PaymentCreateCommand failedPaymentCommand,
            PaymentCreateRequest request
    ) {
        if (failedPaymentCommand == null) {
            return;
        }
        if (paymentService.findByOrderId(failedPaymentCommand.orderId()).isPresent()) {
            return;
        }

        try {
            paymentService.createFailedPayment(failedPaymentCommand, request.pgKey(), request.payWay());
            log.warn("[PORTONE_PAYMENT] rejected paid payment recorded as FAILED. orderId={} pgKey={}",
                    failedPaymentCommand.orderId(), request.pgKey());
        } catch (RuntimeException recordException) {
            log.error("[PORTONE_PAYMENT] failed to record rejected payment. orderId={} pgKey={}",
                    failedPaymentCommand.orderId(), request.pgKey(), recordException);
        }
    }

    /**
     * 우리 서버가 받을 수 없는 PAID 결제라면 PortOne 취소를 시도한다
     * 이미 payment가 있으면 다른 흐름에서 처리 중일 수 있어 여기서는 건드리지 않는다
     */
    private boolean cancelRejectedPayment(
            PaymentCreateRequest request,
            PaymentGatewayResponse gatewayResponse
    ) {
        if (!"PAID".equals(gatewayResponse.status())) {
            return false;
        }

        Optional<Payment> existingPayment = paymentService.findByPgKey(request.pgKey());
        if (existingPayment.isPresent()) {
            log.warn("[PORTONE_PAYMENT] cancel skipped because payment already exists. orderId={} pgKey={} status={}",
                    request.orderId(), request.pgKey(), existingPayment.get().getStatus());
            return false;
        }

        cancelGatewayPaymentSafely(request.pgKey(), gatewayResponse.totalAmount(), "PAYMENT_CREATE_REJECTED");
        return true;
    }

    /**
     * 서명 검증이 끝난 웹훅을 결제 이벤트로 풀어낸다
     * Paid 웹훅이 payment보다 먼저 왔다면 실패가 아니라 잠시 보류한다
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
                log.info("[PORTONE_WEBHOOK] payment is not saved yet. webhook deferred. pgKey={}", event.pgKey());
                webhookService.deferPaidWebhook(webhook, event.eventType(), event.pgKey());
                return PaymentCancelDecision.defer();
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
     * PortOne 결제 이벤트를 실제 Payment 상태 변경으로 연결한다
     * 같은 pgKey는 분산락 안에서 하나씩 처리된다
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
            default:
                log.warn("[PORTONE_WEBHOOK] unsupported event type. type={}", event.eventType());
                return PaymentCancelDecision.none();
        }
    }

    /**
     * Failed 또는 Cancelled 웹훅을 기존 Payment에 반영한다
     * payment가 없으면 orderId를 알 수 없으므로 로그만 남기고 끝낸다
     */
    private PaymentCancelDecision failExistingPayment(String pgKey) {
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
     * Paid 웹훅으로 결제를 최종 확정한다
     * 확정 직전에 주문 상태, 결제 기한, 좌석을 다시 확인하고 문제가 있으면 FAILED로 돌린다
     */
    private PaymentCancelDecision confirmPaidWebhook(String pgKey) {

        // 같은 결제의 웹훅들이 동시에 상태를 바꾸지 못하게 row를 잠근다
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

        // 웹훅은 최종 확정 지점이라 한 번 더 확인한다
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

        orderService.completePayment(order.getId());

        increaseCourseConfirmCount(order, course);

        completeOccupyingSeatQuietly(order);

        log.info("[PORTONE_WEBHOOK] 결제 확정 완료. pgKey={} memberId={}", pgKey, payment.getMemberId());
        return PaymentCancelDecision.none();
    }

    /**
     * 결제 생성보다 먼저 와서 보류된 Paid 웹훅을 이어 처리한다
     * 이 보정으로 payment가 PENDING에 오래 머무는 일을 막는다
     */
    private void resumePendingPaidWebhook(String pgKey) {
        Optional<Webhook> paidWebhook = webhookService.findPendingPaidWebhook(pgKey);
        if (paidWebhook.isEmpty()) {
            return;
        }

        Webhook webhook = webhookService.prepareRetry(
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
                        () -> transactionTemplate.execute(status -> handleTransactionWebhook(
                                new PortOneWebhookEvent(true, PaymentWebhookConstants.WEBHOOK_TRANSACTION_PAID, pgKey),
                                webhook
                        ))
                )
        );

        if (result != null && result.cancelRequired()) {
            try {
                cancelGatewayPayment(result.pgKey(), result.cancelAmount(), result.cancelReason());
                webhookService.failWebhook(webhook, result.cancelReason(), "결제 확정 거절 후 PortOne 취소 완료");
                return;
            } catch (RuntimeException e) {
                webhookService.failWebhook(
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
            webhookService.completeWebhook(webhook);
        }
    }

    /**
     * 코스 기준 분산락을 걸기 위해 payment의 courseId를 찾는다
     */
    private UUID findCourseIdByPayment(Payment payment) {
        Order order = getOrder(payment.getOrderId());
        return order.getCourseId();
    }

    /**
     * 이번 주문을 확정해도 코스 정원을 넘지 않는지 확인한다
     */
    private boolean hasAvailableSeats(Order order, Course course) {
        return course.getConfirmCount() + order.getOrderCount() <= course.getCapacity();
    }

    /**
     * 결제된 수량만큼 코스 확정 인원을 늘린다
     */
    private void increaseCourseConfirmCount(Order order, Course course) {
        for (int i = 0; i < order.getOrderCount(); i++) {
            course.increaseConfirmCount();
        }
    }

    /**
     * 결제 검증에 필요한 주문을 조회한다
     */
    private Order getOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ServiceErrorException(OrderExceptionEnum.ERR_NOT_FOUND_ORDER));
    }

    /**
     * 좌석 검증과 확정 인원 증가에 필요한 코스를 조회한다
     */
    private Course getCourse(UUID courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_COURSE_NOT_FOUND));
    }

    /**
     * 주문 금액 스냅샷을 결제 금액 타입으로 바꾼다
     */
    private Long toLong(BigInteger value) {
        try {
            return value.longValueExact();
        } catch (ArithmeticException e) {
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_AMOUNT_MISMATCH);
        }
    }

    /**
     * 결제 생성 검증 실패 후 PortOne 취소를 시도한다
     * 취소 실패가 원래 예외를 덮지 않도록 로그만 남긴다
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
     * PortOne 결제 취소 API를 호출한다
     */
    private void cancelGatewayPayment(String pgKey, Long amount, String reason) {
        log.warn("[PORTONE_PAYMENT] cancel request. pgKey={} amount={} reason={}", pgKey, amount, reason);
        paymentGateway.cancelPayment(PaymentGatewayRequest.of(pgKey, amount, reason));
    }

    /**
     * Redis 선점 삭제가 실패해도 결제 성공은 DB에 확정하고, Redis 정리 실패는 로그로 남긴다
     */
    private void completeOccupyingSeatQuietly(Order order) {
        try {
            waitingListService.completeOccupyingSeat(order.getCourseId(), order.getMemberId());
        } catch (RuntimeException e) {
            log.error("[PORTONE_WEBHOOK] Redis 선점 확정 처리 실패. orderId={} courseId={} memberId={}",
                    order.getId(), order.getCourseId(), order.getMemberId(), e);
        }
    }

    private record PortOneWebhookEvent(boolean transaction, String eventType, String pgKey) {

        /**
         * PortOne 웹훅에서 결제 처리에 필요한 타입과 pgKey만 꺼낸다
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
