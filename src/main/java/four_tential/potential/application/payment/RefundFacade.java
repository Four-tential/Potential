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
import four_tential.potential.presentation.payment.dto.RefundCourseResponse;
import four_tential.potential.presentation.payment.dto.RefundDetailResponse;
import four_tential.potential.presentation.payment.dto.RefundPreviewResponse;
import four_tential.potential.presentation.payment.dto.RefundResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigInteger;
import java.util.List;
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
        // 1. 검증 및 환불 계획 수립 (트랜잭션 + 비관적 락)
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

    /**
     * 강사 코스 취소 시 해당 코스의 모든 결제 주문을 일괄 전액 환불
     *
     * 호출 시점: 코스 도메인에서 course.cancel() 후 호출
     *   강사 직접 취소든, 관리자가 강사 코스를 강제 취소하든 동일하게 호출한다.
     *   두 경우 모두 귀책이 강사에게 있으므로 refunds.reason = INSTRUCTOR
     *
     *   - 일부 실패해도 전체 중단 없이 나머지를 계속 처리한다.
     *   - MVP: for 문 순차 처리 / 향후: 내부만 비동기로 교체
     */
    public RefundCourseResponse refundAllPaidOrdersForCancelledCourse(UUID courseId) {
        Course course = getCourse(courseId);
        // courseId 기준 환불 가능 주문 List에 담기
        List<Order> refundableOrders = orderRepository.findRefundableOrdersByCourseId(courseId);

        if (refundableOrders.isEmpty()) {
            log.info("[INSTRUCTOR_REFUND] 환불 대상 주문 없음. courseId={}", courseId);
            return RefundCourseResponse.of(courseId, course.getTitle(), 0, 0, 0, 0L);
        }

        log.warn("[INSTRUCTOR_REFUND] 일괄 환불 시작. courseId={} targetCount={}", courseId, refundableOrders.size());

        int totalCount         = refundableOrders.size();
        int refundedCount      = 0;
        int failedCount        = 0;
        Long totalRefundAmount = 0L;

        for (Order order : refundableOrders) {
            try {
                Long refundedAmount = refundSingleOrderForInstructor(order);
                refundedCount++;
                totalRefundAmount += refundedAmount;
            } catch (Exception e) {
                // 실패 이력은 내부에서 REQUIRES_NEW 트랜잭션으로 저장됨
                failedCount++;
                log.error("[INSTRUCTOR_REFUND] 주문 환불 실패. orderId={} memberId={} — 관리자 수동 처리 필요",
                        order.getId(), order.getMemberId(), e);
            }
        }

        log.warn("[INSTRUCTOR_REFUND] 일괄 환불 완료. courseId={} total={} success={} fail={}",
                courseId, totalCount, refundedCount, failedCount);

        return RefundCourseResponse.of(
                courseId, course.getTitle(), totalCount, refundedCount, failedCount, totalRefundAmount
        );
    }

    /**
     * 주문 단건에 대한 강사 취소 환불을 처리
     *
     * PART_REFUNDED 처리의 경우
     *   수강생이 이미 일부 환불받은 경우 completedRefundTotal 을 차감한
     *   나머지 금액만 PortOne 에 요청한다.
     *   예) paid=30,000 / 기환불=10,000 → 이번 환불=20,000
     */
    private Long refundSingleOrderForInstructor(Order order) {
        Payment payment = getPaymentByOrderId(order.getId());

        return paymentLockExecutor.executeWithPgKeyLock(payment.getPgKey(), () -> {

            // 1. 검증 및 환불 계획 수립 (트랜잭션 + 비관적 락)
            InstructorRefundPlan plan = Objects.requireNonNull(
                    transactionTemplate.execute(
                            status -> prepareInstructorRefund(order.getId(), payment.getPgKey())
                    )
            );

            // 2. PortOne 환불 요청 (트랜잭션 밖)
            try {
                paymentGateway.cancelPayment(PaymentGatewayRequest.ofPartial(
                        plan.pgKey(),
                        plan.refundPrice(),              // 이번에 환불할 금액
                        plan.refundPrice(),              // 강사 취소는 남은 금액 전액이므로 두 값 동일
                        RefundConstants.INSTRUCTOR_CANCEL_REASON
                ));
            } catch (RuntimeException e) {
                refundService.createFailed(
                        plan.paymentId(), plan.refundPrice(),
                        plan.cancelCount(), RefundReason.INSTRUCTOR
                );
                log.error("[INSTRUCTOR_REFUND] PortOne 환불 실패. paymentId={} pgKey={} amount={}",
                        plan.paymentId(), plan.pgKey(), plan.refundPrice(), e);
                throw new ServiceErrorException(PaymentExceptionEnum.ERR_REFUND_PROCESS_FAILED);
            }

            // 3. DB 확정 (새 트랜잭션 + 비관적 락 재획득)
            transactionTemplate.execute(status -> {
                completeInstructorRefund(plan);
                return null;
            });

            // 4. Redis 잔여석 복구 (실패해도 환불 결과에 영향 없음)
            recoverCapacityQuietly(plan.courseId(), plan.memberId(), plan.cancelCount());

            return plan.refundPrice();
        });
    }

    /**
     * 강사 취소 환불 검증 및 계획 수립
     */
    private InstructorRefundPlan prepareInstructorRefund(UUID orderId, String pgKey) {
        Payment payment = paymentService.getByPgKeyForUpdate(pgKey);

        // 결제 상태 검증 PAID, PART_REFUNDED 만 허용
        refundService.validateRefundablePaymentStatus(payment);

        Order order = getOrder(orderId);
        validateOrderForInstructorRefund(order);

        Long completedRefundTotal = refundService.getCompletedRefundTotal(payment.getId());
        if (completedRefundTotal == null) completedRefundTotal = 0L;

        // paid_total_price - completedRefundTotal = 남은 환불 가능 금액
        Long remainingRefundablePrice = payment.getPaidTotalPrice() - completedRefundTotal;
        if (remainingRefundablePrice <= 0) {
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_ALREADY_FULLY_REFUNDED);
        }

        return new InstructorRefundPlan(
                payment.getId(),
                payment.getPgKey(),
                order.getId(),
                order.getCourseId(),
                order.getMemberId(),
                order.getOrderCount(),       // cancelCount - confirmCount 감소에 사용
                remainingRefundablePrice     // refundPrice - 남은 전액
        );
    }

    /**
     * PortOne 환불 성공 후 DB 를 확정한다 (강사 취소 전용)
     */
    private void completeInstructorRefund(InstructorRefundPlan plan) {
        Payment payment = paymentService.getByPgKeyForUpdate(plan.pgKey());

        // 1. 환불 이력 저장 (reason = INSTRUCTOR)
        refundService.createCompleted(payment, plan.refundPrice(), plan.cancelCount(), RefundReason.INSTRUCTOR);

        // 2. payments.status = REFUNDED
        paymentService.refund(payment);

        // 3. orders.status = CANCELLED (orderCount 감소 없음)
        orderService.cancelOrderForInstructor(plan.orderId());

        // 4. courses.confirm_count 감소
        Course course = getCourse(plan.courseId());
        decreaseCourseConfirmCount(course, plan.cancelCount());

        log.info("[INSTRUCTOR_REFUND] 단건 확정 완료. paymentId={} pgKey={} amount={}",
                payment.getId(), payment.getPgKey(), plan.refundPrice());
    }

    /**
     * 강사 취소 환불 대상 주문 상태 검증
     * 수강생 직접 환불과 달리 OrderStatus.CONFIRMED 도 허용
     */
    private void validateOrderForInstructorRefund(Order order) {
        if (order.getStatus() != OrderStatus.PAID
                && order.getStatus() != OrderStatus.CONFIRMED) {
            throw new ServiceErrorException(OrderExceptionEnum.ERR_INVALID_ORDER_STATUS);
        }
    }

    /**
     * 강사 취소 환불을 위한 record
     * 항상 전액 환불, 소유자 검증 무관.
     * memberId는 recoverCapacityQuietly 호출 시 필요
     */
    private record InstructorRefundPlan(
            UUID paymentId,
            String pgKey,
            UUID orderId,
            UUID courseId,
            UUID memberId,
            int cancelCount,
            Long refundPrice   // 남은 paidTotalPrice 전액
    ) {}


    /**
     * 환불 단건 조회
     */
    public RefundDetailResponse getMyRefund(UUID memberId, UUID refundId) {
        return refundService.getMyRefund(refundId, memberId);
    }
}
