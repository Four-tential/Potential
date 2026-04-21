package four_tential.potential.application.order;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.OrderExceptionEnum;
import four_tential.potential.domain.course.course.CourseRepository;
import four_tential.potential.common.exception.domain.CourseExceptionEnum;
import four_tential.potential.domain.course.course.Course;
import four_tential.potential.domain.order.Order;
import four_tential.potential.domain.order.OrderRepository;
import four_tential.potential.domain.order.OrderStatus;
import four_tential.potential.presentation.order.dto.OrderAdminStatusUpdateRequest;
import four_tential.potential.presentation.order.dto.OrderAdminStatusUpdateResponse;
import four_tential.potential.presentation.order.dto.OrderCreateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CourseRepository courseRepository;
    private final WaitingListService waitingListService;
    private final ApplicationContext applicationContext;

    /**
     * 주문 생성 (DB 저장)
     */
    @Transactional
    public Order createOrder(UUID memberId, OrderCreateRequest request) {
        // 동일 시간대 중복 예약 체크
        checkDuplicateTimeCourse(memberId, request.courseId());

        BigInteger coursePrice = request.priceSnap();
        String courseTitle = request.titleSnap();

        Order order = Order.register(
                memberId,
                request.courseId(),
                request.orderCount(),
                coursePrice,
                courseTitle
        );
        return orderRepository.save(order);
    }

    /**
     * 동일 시간대 중복 예약 체크
     * TODO: Course 도메인 구현 후 실제 시간대(Time Slot) 비교 로직 추가 필요
     */
    private void checkDuplicateTimeCourse(UUID memberId, UUID courseId) {
        log.info("동일 시간대 중복 예약 체크 중 (Placeholder): memberId={}, courseId={}", memberId, courseId);
        
        // 시나리오: 
        // 1. 요청한 courseId의 시작/종료 시간을 조회
        // 2. 해당 회원의 기존 PAID, PENDING 주문들 중 시간대가 겹치는 코스가 있는지 DB 조회
        // 3. 존재한다면 OrderExceptionEnum.ERR_ALREADY_RESERVED 예외 발생
    }

    /**
     * 주문 상세 조회
     */
    @Transactional(readOnly = true)
    public Order getOrderDetails(UUID orderId, UUID memberId) {
        return orderRepository.findOrderDetailsById(orderId, memberId)
                .orElseThrow(() -> new ServiceErrorException(OrderExceptionEnum.ERR_NOT_FOUND_ORDER));
    }

    /**
     * 나의 주문 목록 조회
     */
    @Transactional(readOnly = true)
    public Page<Order> getMyOrders(UUID memberId, Pageable pageable) {
        return orderRepository.findMyOrders(memberId, pageable);
    }

    /**
     * 결제 완료 처리
     */
    @Transactional
    public void completePayment(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ServiceErrorException(OrderExceptionEnum.ERR_NOT_FOUND_ORDER));

        order.completePayment();
        log.info("주문 결제 완료 처리됨: orderId={}", orderId);
    }

    /**
     * 주문 취소 처리
     */
    @Transactional
    public Order cancelOrder(UUID orderId, UUID memberId) {
        Order order = orderRepository.findOrderDetailsById(orderId, memberId)
                .orElseThrow(() -> new ServiceErrorException(OrderExceptionEnum.ERR_NOT_FOUND_ORDER));

        Course course = courseRepository.findById(order.getCourseId())
                .orElseThrow(() -> new ServiceErrorException(CourseExceptionEnum.ERR_NOT_FOUND_COURSE));

        order.cancel(course.getStartAt(), LocalDateTime.now());

        return order;
    }

    /**
     * 환불 완료 후 주문 수량을 차감한다.
     * PG 환불이 성공한 뒤 결제 트랜잭션 안에서만 호출한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Order applyRefund(UUID orderId, UUID memberId, int cancelCount) {
        Order order = orderRepository.findOrderDetailsById(orderId, memberId)
                .orElseThrow(() -> new ServiceErrorException(OrderExceptionEnum.ERR_NOT_FOUND_ORDER));

        order.applyRefund(cancelCount, LocalDateTime.now());
        return order;
    }

    /**
     * 만료된 주문 자동 만료 처리 (단일 배치)
     * 개별 주문 처리는 독립된 트랜잭션에서 수행하여 낙관적 락 충돌 시 배치가 롤백되지 않도록 합니다.
     */
    public OrderBatchResult processExpiredBatch(LocalDateTime now, int batchSize) {
        // 조회는 트랜잭션 없이 혹은 별도 트랜잭션으로 수행 (영속성 컨텍스트 분리를 위해 ID만 먼저 확보할 수도 있으나 여기서는 단순화)
        Slice<Order> expiredOrdersSlice = orderRepository.findAllByStatusAndExpireAtBefore(
                OrderStatus.PENDING, now, PageRequest.of(0, batchSize));

        int fetchedCount = expiredOrdersSlice.getNumberOfElements();
        if (fetchedCount == 0) {
            return new OrderBatchResult(0, 0);
        }

        OrderService self = applicationContext.getBean(OrderService.class);
        int successCount = 0;

        for (Order order : expiredOrdersSlice) {
            if (self.expireOrderInNewTransaction(order.getId())) {
                successCount++;
            }
        }

        return new OrderBatchResult(fetchedCount, successCount);
    }

    /**
     * 단일 주문을 독립된 트랜잭션(REQUIRES_NEW)으로 만료 처리합니다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean expireOrderInNewTransaction(UUID orderId) {
        try {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new ServiceErrorException(OrderExceptionEnum.ERR_NOT_FOUND_ORDER));
            
            order.expire();
            orderRepository.saveAndFlush(order); 

            // DB 커밋 성공 후에만 Redis 복구 수행
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    rollbackRedisSeatQuietly(order);
                }
            });

            log.info("주문 만료 처리됨: orderId={}, courseId={}", order.getId(), order.getCourseId());
            return true;
        } catch (Exception e) {
            log.error("주문 만료 처리 중 예외 발생 (낙관적 락 등): orderId={}, reason={}", orderId, e.getMessage());
            return false;
        }
    }

    /**
     * 결제 완료된 주문 자동 확정 처리 (단일 배치)
     */
    public OrderBatchResult processConfirmedBatch(LocalDateTime now, int batchSize) {
        List<Order> ordersToConfirm = orderRepository.findPaidOrdersToConfirm(now, PageRequest.of(0, batchSize));

        int fetchedCount = ordersToConfirm.size();
        if (fetchedCount == 0) {
            return new OrderBatchResult(0, 0);
        }

        OrderService self = applicationContext.getBean(OrderService.class);
        int successCount = 0;

        for (Order order : ordersToConfirm) {
            if (self.confirmOrderInNewTransaction(order.getId())) {
                successCount++;
            }
        }

        return new OrderBatchResult(fetchedCount, successCount);
    }

    /**
     * 단일 주문을 독립된 트랜잭션(REQUIRES_NEW)으로 확정 처리합니다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean confirmOrderInNewTransaction(UUID orderId) {
        try {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new ServiceErrorException(OrderExceptionEnum.ERR_NOT_FOUND_ORDER));

            order.confirm();
            orderRepository.saveAndFlush(order);

            log.info("주문 확정 처리됨: orderId={}, courseId={}", order.getId(), order.getCourseId());
            return true;
        } catch (Exception e) {
            log.error("주문 확정 처리 중 예외 발생: orderId={}, reason={}", orderId, e.getMessage());
            return false;
        }
    }

    /**
     * 관리자에 의한 주문 상태 강제 변경
     */
    @Transactional
    public OrderAdminStatusUpdateResponse updateOrderStatusByAdmin(UUID orderId, OrderAdminStatusUpdateRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ServiceErrorException(OrderExceptionEnum.ERR_NOT_FOUND_ORDER));

        OrderStatus previousStatus = order.getStatus();
        OrderStatus targetStatus = request.targetStatus();

        order.updateStatusByAdmin(targetStatus);

        // 실제로 좌석을 점유하던 상태에서 취소/만료 상태로 변하는 경우만 Redis 복구 예약
        if (isSeatRestorationRequired(previousStatus, targetStatus)) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    rollbackRedisSeatQuietly(order);
                }
            });
        }

        return OrderAdminStatusUpdateResponse.of(order, previousStatus);
    }

    /**
     * 좌석 복구가 필요한 상태 전이인지 확인
     */
    private boolean isSeatRestorationRequired(OrderStatus previous, OrderStatus target) {
        // 이미 취소나 만료인 상태에서는 복구 불필요
        if (previous == OrderStatus.CANCELLED || previous == OrderStatus.EXPIRED) {
            return false;
        }
        // 대상 상태가 취소나 만료인 경우에만 복구 필요
        return target == OrderStatus.CANCELLED || target == OrderStatus.EXPIRED;
    }

    public record OrderBatchResult(int fetchedCount, int successCount) {}

    private void rollbackRedisSeatQuietly(Order order) {
        try {
            waitingListService.rollbackOccupiedSeat(order.getCourseId(), order.getMemberId());
        } catch (Exception e) {
            log.error("Redis 재고 복구 실패: orderId={}, courseId={}, reason={}",
                    order.getId(), order.getCourseId(), e.getMessage());
        }
    }
}
