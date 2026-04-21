package four_tential.potential.application.payment;

import four_tential.potential.application.order.OrderService;
import four_tential.potential.application.order.WaitingListService;
import four_tential.potential.application.payment.consts.RefundConstants;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.OrderExceptionEnum;
import four_tential.potential.common.exception.domain.PaymentExceptionEnum;
import four_tential.potential.domain.course.course.Course;
import four_tential.potential.domain.course.course.CourseRepository;
import four_tential.potential.domain.order.Order;
import four_tential.potential.domain.order.OrderRepository;
import four_tential.potential.domain.order.OrderStatus;
import four_tential.potential.domain.payment.entity.Payment;
import four_tential.potential.domain.payment.entity.Refund;
import four_tential.potential.domain.payment.enums.RefundReason;
import four_tential.potential.domain.payment.port.PaymentGateway;
import four_tential.potential.domain.payment.port.PaymentGatewayRequest;
import four_tential.potential.presentation.payment.dto.RefundPreviewResponse;
import four_tential.potential.presentation.payment.dto.RefundResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigInteger;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefundFacade {

    private final RefundService refundService;
    private final PaymentService paymentService;
    private final PaymentGateway paymentGateway;
    private final OrderService orderService;
    private final WaitingListService waitingListService;
    private final OrderRepository orderRepository;
    private final CourseRepository courseRepository;
    private final PaymentDistributedLockExecutor paymentLockExecutor;
    private final TransactionTemplate transactionTemplate;

    /**
     * 환불 가능 여부를 조회
     * 부분 환불 후에도 단가는 주문의 1장 가격 스냅샷을 그대로 사용한다.
     */
    public RefundPreviewResponse getRefundPreview(UUID memberId, UUID paymentId) {
        Payment payment = paymentService.getById(paymentId);
        Order order = getOrder(payment.getOrderId());
        Course course = getCourse(order.getCourseId());

        return refundService.getRefundPreview(
                payment,
                memberId,
                order.getTitleSnap(),
                course.getStartAt(),
                order.getOrderCount(),
                toLong(order.getPriceSnap())
        );
    }

    /**
     * 결제된 주문의 수강생 취소 환불을 처리
     * 주문 취소 흐름에서 호출되며, 주문을 먼저 취소하지 않고 환불 성공 후 DB를 정리한다.
     */
    public RefundResponse refundPaidOrderByStudent(UUID memberId, UUID orderId, int cancelCount) {

        // 락을 잡기 전에 먼저 조회해서 pgKey와 courseId를 알아둔다
        Order order = getOrder(orderId);
        Payment payment = getPaymentByOrderId(orderId);

        // pgKey 기준 분산락 적용
        return paymentLockExecutor.executeWithPgKeyLock(payment.getPgKey(), () ->
                // courseId 기준 분산락 적용 - 코스의 confirmCount를 동시에 줄이는 충돌 방지
                paymentLockExecutor.executeWithCourseLock(order.getCourseId(), () ->
                        refundInLock(memberId, orderId, cancelCount, payment.getPgKey())
                )
        );
    }

    private RefundResponse refundInLock(UUID memberId, UUID orderId, int cancelCount, String pgKey) {
        // 1.
        RefundPlan plan = Objects.requireNonNull(transactionTemplate.execute(
                status -> prepareRefund(memberId, orderId, cancelCount, pgKey)
        ));

        // 2. PortOne에 실제 환불 요청 (트랜잭션 밖)
        try {
            cancelGatewayPayment(plan);
        } catch (RuntimeException e) {
            // 실패 이력을 별도 트랜잭션(REQUIRES_NEW)으로 저장
            refundService.createFailed(plan.paymentId(), plan.refundPrice(), plan.cancelCount(), RefundReason.CANCEL);
            log.error("[PORTONE_REFUND] PortOne refund failed. paymentId={} pgKey={} amount={}",
                    plan.paymentId(), plan.pgKey(), plan.refundPrice(), e);
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_REFUND_PROCESS_FAILED);
        }

        // 3. PortOne 성공 후 DB 확정
        RefundResult result;
        try {
            result = Objects.requireNonNull(transactionTemplate.execute(
                    status -> completeRefund(memberId, plan)
            ));
        } catch (RuntimeException e) {
            log.error("[PORTONE_REFUND] DB update failed after PortOne refund success. paymentId={} pgKey={} amount={}",
                    plan.paymentId(), plan.pgKey(), plan.refundPrice(), e);
            throw e;
        }

        // 4. Redis 잔여석 복구 (트랜잭션 밖, 실패해도 응답은 성공)
        recoverCapacityQuietly(result.courseId(), result.memberId(), plan.cancelCount());
        return result.response();
    }

    /**
     * PortOne에 돈을 돌려달라고 요청하기 전, 우리 DB 기준으로 환불 가능한지 검증한다.
     * 여기서 실패하면 외부 환불 요청은 절대 보내지 않는다.
     */
    private RefundPlan prepareRefund(UUID memberId, UUID orderId, int cancelCount, String pgKey) {
        Payment payment = paymentService.getByPgKeyForUpdate(pgKey);
        if (!payment.getOrderId().equals(orderId)) {
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_NOT_FOUND_PAYMENT);
        }

        refundService.validateOwner(payment, memberId);
        refundService.validateRefundablePaymentStatus(payment);

        Order order = getOrder(payment.getOrderId());
        Course course = getCourse(order.getCourseId());
        validateOrderForRefund(order);
        validateRefundPeriod(course);
        validateCancelCount(cancelCount, order.getOrderCount());

        Long unitPrice = toLong(order.getPriceSnap());
        Long completedRefundTotal = refundService.getCompletedRefundTotal(payment.getId());
        if (completedRefundTotal == null) {
            completedRefundTotal = 0L;
        }

        Long remainingRefundablePrice = payment.getPaidTotalPrice() - completedRefundTotal;
        if (remainingRefundablePrice <= 0) {
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_ALREADY_FULLY_REFUNDED);
        }

        boolean fullRefund = cancelCount == order.getOrderCount();
        Long refundPrice = calculateRefundPrice(
                unitPrice,
                cancelCount,
                remainingRefundablePrice,
                fullRefund
        );

        return new RefundPlan(
                payment.getId(),
                payment.getPgKey(),
                order.getId(),
                order.getCourseId(),
                order.getMemberId(),
                order.getTitleSnap(),
                cancelCount,
                unitPrice,
                refundPrice,
                remainingRefundablePrice,
                fullRefund
        );
    }

    /**
     * PortOne 환불 성공 후 우리 DB를 확정한다.
     * 결제 상태만 전체/부분으로 나뉘고, 주문 차감 흐름은 같이 사용한다.
     */
    private RefundResult completeRefund(UUID memberId, RefundPlan plan) {
        Payment payment = paymentService.getByPgKeyForUpdate(plan.pgKey());
        refundService.validateOwner(payment, memberId);
        refundService.validateRefundablePaymentStatus(payment);

        Refund refund = refundService.createCompleted(
                payment,
                plan.refundPrice(),
                plan.cancelCount(),
                RefundReason.CANCEL
        );

        return plan.fullRefund()
                ? completeFullRefund(memberId, plan, payment, refund)
                : completePartialRefund(memberId, plan, payment, refund);
    }

    /**
     * 남은 수강권을 모두 취소하는 경우다.
     * Payment는 REFUNDED가 되고, 주문은 수량이 0이 되면서 CANCELLED가 된다.
     */
    private RefundResult completeFullRefund(UUID memberId, RefundPlan plan, Payment payment, Refund refund) {
        paymentService.refund(payment);
        return applyRefundToOrder(memberId, plan, payment, refund);
    }

    /**
     * 수강권 일부만 취소하는 경우다.
     * Payment는 PART_REFUNDED가 되고, 주문은 남은 수량을 계속 들고 간다.
     */
    private RefundResult completePartialRefund(UUID memberId, RefundPlan plan, Payment payment, Refund refund) {
        paymentService.partRefund(payment);
        return applyRefundToOrder(memberId, plan, payment, refund);
    }

    /**
     * 환불된 수량만큼 주문과 코스 확정 인원을 줄인다.
     * 이 메서드는 전체/부분 환불이 공통으로 사용하는 마지막 DB 반영 단계다.
     */
    private RefundResult applyRefundToOrder(UUID memberId, RefundPlan plan, Payment payment, Refund refund) {
        Order updatedOrder = orderService.applyRefund(plan.orderId(), memberId, plan.cancelCount());
        Course course = getCourse(plan.courseId());
        decreaseCourseConfirmCount(course, plan.cancelCount());

        RefundResponse response = RefundResponse.of(
                refund,
                payment.getId(),
                plan.courseTitle(),
                updatedOrder.getOrderCount(),
                plan.unitPrice(),
                payment.getStatus()
        );

        log.info("[PORTONE_REFUND] refund completed. paymentId={} pgKey={} amount={} cancelCount={}",
                payment.getId(), payment.getPgKey(), plan.refundPrice(), plan.cancelCount());
        return new RefundResult(plan.courseId(), plan.memberId(), response);
    }

    private void validateOrderForRefund(Order order) {
        if (order.getStatus() != OrderStatus.PAID) {
            throw new ServiceErrorException(OrderExceptionEnum.ERR_INVALID_ORDER_STATUS);
        }
    }

    private void validateRefundPeriod(Course course) {
        if (!refundService.isRefundable(course.getStartAt())) {
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_REFUND_NOT_ALLOWED);
        }
    }

    private void validateCancelCount(int cancelCount, int currentOrderCount) {
        if (cancelCount < 1) {
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_CANCEL_COUNT_INVALID);
        }
        if (cancelCount > currentOrderCount) {
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_CANCEL_COUNT_EXCEEDED);
        }
    }

    private Long calculateRefundPrice(Long unitPrice, int cancelCount, Long remainingRefundablePrice, boolean fullRefund) {
        if (fullRefund) {
            return remainingRefundablePrice;
        }

        long refundPrice;
        try {
            refundPrice = Math.multiplyExact(unitPrice, cancelCount);
        } catch (ArithmeticException e) {
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_AMOUNT_MISMATCH);
        }

        if (refundPrice > remainingRefundablePrice) {
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_CANCEL_COUNT_EXCEEDED);
        }
        return refundPrice;
    }

    /**
     * PortOne에 취소 금액과 현재 취소 가능한 잔여 금액을 함께 전달한다.
     * 전액 취소 시에는 amount == currentCancellableAmount 이므로 동일한 값을 넘긴다.
     */
    private void cancelGatewayPayment(RefundPlan plan) {
        log.warn("[PORTONE_REFUND] refund request. pgKey={} amount={} cancelCount={} currentCancellableAmount={}",
                plan.pgKey(), plan.refundPrice(), plan.cancelCount(), plan.remainingRefundablePrice());

        paymentGateway.cancelPayment(PaymentGatewayRequest.ofPartial(
                plan.pgKey(),
                plan.refundPrice(),                 // 이번에 취소할 금액
                plan.remainingRefundablePrice(),     // 현재 취소 가능한 잔여 금액
                RefundConstants.STUDENT_CANCEL_REASON
        ));
    }

    /**
     * Redis 잔여석 복구 (취소된 수량만큼) 및 점유 정보 정리
     * 돈과 DB 처리는 이미 끝났기 때문에 Redis 복구 실패는 로그로 남기고 응답은 실패시키지 않는다.
     */
    private void recoverCapacityQuietly(UUID courseId, UUID memberId, int cancelCount) {
        try {
            waitingListService.recoverCapacity(courseId, memberId, cancelCount);
        } catch (RuntimeException e) {
            log.error("[PORTONE_REFUND] Redis capacity recovery failed. courseId={} memberId={} count={}",
                    courseId, memberId, cancelCount, e);
        }
    }

    private void decreaseCourseConfirmCount(Course course, int cancelCount) {
        for (int i = 0; i < cancelCount; i++) {
            course.decreaseConfirmCount();
        }
    }

    private Order getOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ServiceErrorException(OrderExceptionEnum.ERR_NOT_FOUND_ORDER));
    }

    private Payment getPaymentByOrderId(UUID orderId) {
        return paymentService.findByOrderId(orderId)
                .orElseThrow(() -> new ServiceErrorException(PaymentExceptionEnum.ERR_NOT_FOUND_PAYMENT));
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

    private record RefundPlan(
            UUID paymentId,
            String pgKey,
            UUID orderId,
            UUID courseId,
            UUID memberId,
            String courseTitle,
            int cancelCount,
            Long unitPrice,
            Long refundPrice,
            Long remainingRefundablePrice,  // 현재 취소 가능한 잔여 금액
            boolean fullRefund
    ) {
    }

    private record RefundResult(
            UUID courseId,
            UUID memberId,
            RefundResponse response
    ) {
    }
}
