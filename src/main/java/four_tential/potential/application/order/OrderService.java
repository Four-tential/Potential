package four_tential.potential.application.order;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.OrderExceptionEnum;
import four_tential.potential.domain.course.course.CourseRepository;
import four_tential.potential.common.exception.domain.CourseExceptionEnum;
import four_tential.potential.domain.course.course.Course;
import four_tential.potential.domain.order.Order;
import four_tential.potential.domain.order.OrderRepository;
import four_tential.potential.domain.order.OrderStatus;
import four_tential.potential.infra.redis.annotation.DistributedLock;
import four_tential.potential.presentation.order.dto.OrderAdminStatusUpdateRequest;
import four_tential.potential.presentation.order.dto.OrderAdminStatusUpdateResponse;
import four_tential.potential.presentation.order.dto.OrderCreateRequest;
import four_tential.potential.presentation.order.dto.OrderInventoryReconcileResponse;
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
    // TODO: courseRepository는 주입되면 안됌. courseFacade 주입으로 변경
    private final CourseRepository courseRepository;
    private final WaitingListService waitingListService;
    private final ApplicationContext applicationContext;

    /**
     * 주문 생성 (DB 저장)
     * 회원 단위 분산 락을 적용하여 동일 시간대 중복 예약 체크의 원자성을 보장
     */
    @Transactional
    @DistributedLock(key = "'order:member:' + #memberId")
    public Order createOrder(UUID memberId, OrderCreateRequest request) {
        // 코스 정보 조회
        Course course = courseRepository.findById(request.courseId())
                .orElseThrow(() -> new ServiceErrorException(CourseExceptionEnum.ERR_NOT_FOUND_COURSE));

        // 동일 시간대 중복 예약 재검증
        // Facade 에서 1차 체크를 수행하지만, 동시성 환경에서 안전을 위해 락 내부에서 최종 확인한다.
        boolean hasOverlap = orderRepository.hasOverlappingReservation(
                memberId,
                course.getStartAt(),
                course.getEndAt()
        );

        if (hasOverlap) {
            log.warn("중복 예약이 감지되었습니다: memberId={}, courseId={}", memberId, course.getId());
            throw new ServiceErrorException(OrderExceptionEnum.ERR_ALREADY_RESERVED);
        }

        // 주문 등록
        Order order = Order.register(
                memberId,
                request.courseId(),
                request.orderCount(),
                course.getPrice(),
                course.getTitle()
        );
        return orderRepository.save(order);
    }

    /**
     * 동일 시간대 중복 예약 체크
     */
    @Transactional(readOnly = true)
    public void checkDuplicateTimeCourse(UUID memberId, UUID courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ServiceErrorException(CourseExceptionEnum.ERR_NOT_FOUND_COURSE));
        
        log.info("동일 시간대 중복 예약 체크 중: memberId={}, courseId={}", memberId, course.getId());
        
        boolean hasOverlap = orderRepository.hasOverlappingReservation(
                memberId, 
                course.getStartAt(), 
                course.getEndAt()
        );

        if (hasOverlap) {
            throw new ServiceErrorException(OrderExceptionEnum.ERR_ALREADY_RESERVED);
        }
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
     * 강사 코스 취소 시 주문 status 를 CANCELLED 로 변경
     * orderCount 감소 없이 status 만 변경
     */
    @Transactional
    public void cancelOrderForInstructor(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ServiceErrorException(OrderExceptionEnum.ERR_NOT_FOUND_ORDER));
        order.cancelByInstructor();
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

    /**
     * 특정 코스의 재고 정합성 복구
     * DB의 유효 주문 좌석 수를 기준으로 Redis의 잔여석 수치를 강제 업데이트합니다.
     */
    @DistributedLock(key = "'order:course:' + #courseId")
    @Transactional(readOnly = true)
    public OrderInventoryReconcileResponse reconcileInventory(UUID courseId) {
        return performReconcile(courseId);
    }

    /**
     * 재고 정보가 초기화되지 않은 경우에만 정합성 복구를 수행합니다.
     * Happy Path(이미 초기화됨)에서의 성능을 위해 락 없이 1차 확인을 수행합니다.
     */
    public void reconcileInventoryIfNecessary(UUID courseId) {
        if (!waitingListService.isCapacityInitialized(courseId)) {
            // 1차 체크 통과 시에만 분산 락을 획득하고 다시 확인(Double-Check)
            applicationContext.getBean(OrderService.class).reconcileInventoryLocked(courseId);
        }
    }

    /**
     * 분산 락 범위 내에서 안전하게 재고를 초기화합니다.
     * 락 내부에서 실행되므로 트랜잭션과 락 설정을 명시적으로 가져갑니다.
     */
    @DistributedLock(key = "'order:course:' + #courseId")
    @Transactional(readOnly = true)
    public void reconcileInventoryLocked(UUID courseId) {
        if (!waitingListService.isCapacityInitialized(courseId)) {
            log.info("재고 미초기화 감지, 복구 프로세스 시작: courseId={}", courseId);
            performReconcile(courseId);
        }
    }

    /**
     * 실제 재고 복구 로직을 수행하는 내부 메서드
     */
    private OrderInventoryReconcileResponse performReconcile(UUID courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ServiceErrorException(CourseExceptionEnum.ERR_NOT_FOUND_COURSE));

        // DB에서 유효한 주문(PENDING, PAID, CONFIRMED)의 좌석 합계 조회
        int occupiedSeats = orderRepository.sumOrderCountByCourseIdAndStatuses(
                courseId,
                List.of(OrderStatus.PENDING, OrderStatus.PAID, OrderStatus.CONFIRMED)
        );

        // 새 잔여석 계산 (총 정원 - 점유 좌석)
        long newCapacity = Math.max(0, (long) course.getCapacity() - occupiedSeats);

        // Redis 강제 업데이트 및 대기열 승격 시도 (내부적으로 재진입 락 발생)
        waitingListService.updateCapacity(courseId, newCapacity);

        log.info("재고 정합성 복구 완료: courseId={}, 총정원={}, DB점유={}, Redis잔여석={}",
                courseId, course.getCapacity(), occupiedSeats, newCapacity);

        return OrderInventoryReconcileResponse.of(courseId, course.getCapacity(), occupiedSeats, newCapacity);
    }
}
