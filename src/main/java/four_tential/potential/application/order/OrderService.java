package four_tential.potential.application.order;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.OrderExceptionEnum;
import four_tential.potential.domain.order.Order;
import four_tential.potential.domain.order.OrderRepository;
import four_tential.potential.domain.order.OrderStatus;
import four_tential.potential.presentation.order.dto.OrderCreateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final WaitingListService waitingListService;

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
     * 만료된 주문 자동 만료 처리 (단일 배치)
     * Scheduler에서 호출하며, 개별 호출마다 트랜잭션이 커밋됩니다.
     */
    @Transactional
    public int processExpiredBatch(LocalDateTime now, int batchSize) {
        // PENDING 상태인 주문을 EXPIRED로 바꾸므로 항상 0페이지를 읽으면 새로운 대상이 나옵니다.
        Slice<Order> expiredOrdersSlice = orderRepository.findAllByStatusAndExpireAtBefore(
                OrderStatus.PENDING, now, PageRequest.of(0, batchSize));

        if (expiredOrdersSlice.isEmpty()) {
            return 0;
        }

        for (Order order : expiredOrdersSlice) {
            order.expire();
            // Redis 재고 해제: 선점 정보가 남아 있다면 복구 시도
            waitingListService.rollbackOccupiedSeat(order.getCourseId(), order.getMemberId());
            log.info("주문 만료 처리됨: orderId={}, courseId={}", order.getId(), order.getCourseId());
        }
        
        return expiredOrdersSlice.getNumberOfElements();
    }
}
