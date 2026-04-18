package four_tential.potential.application.order;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.OrderExceptionEnum;
import four_tential.potential.domain.order.Order;
import four_tential.potential.domain.order.OrderRepository;
import four_tential.potential.domain.order.OrderStatus;
import four_tential.potential.presentation.order.dto.OrderCreateRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private WaitingListService waitingListService;
    @InjectMocks private OrderService orderService;

    @Test
    @DisplayName("주문을 성공적으로 생성하고 저장한다")
    void createOrder_success() {
        // given
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        OrderCreateRequest request = new OrderCreateRequest(
                courseId,
                2,
                BigInteger.valueOf(50000),
                "테스트 강의"
        );

        given(orderRepository.save(any(Order.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        Order result = orderService.createOrder(memberId, request);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getMemberId()).isEqualTo(memberId);
        assertThat(result.getCourseId()).isEqualTo(courseId);
        assertThat(result.getOrderCount()).isEqualTo(2);
        assertThat(result.getPriceSnap()).isEqualTo(BigInteger.valueOf(50000));
        
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    @DisplayName("주문 상세 조회 성공 시 주문 정보를 반환한다")
    void getOrderDetails_success() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Order order = mock(Order.class);
        given(orderRepository.findOrderDetailsById(orderId, memberId)).willReturn(Optional.of(order));

        // when
        Order result = orderService.getOrderDetails(orderId, memberId);

        // then
        assertThat(result).isEqualTo(order);
        verify(orderRepository).findOrderDetailsById(orderId, memberId);
    }

    @Test
    @DisplayName("주문 상세 조회 실패 시 예외를 발생시킨다")
    void getOrderDetails_fail_notFound() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        given(orderRepository.findOrderDetailsById(orderId, memberId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> orderService.getOrderDetails(orderId, memberId))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(OrderExceptionEnum.ERR_NOT_FOUND_ORDER.getMessage());
    }

    @Test
    @DisplayName("나의 주문 목록을 성공적으로 조회한다")
    void getMyOrders_success() {
        // given
        UUID memberId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        Order order = mock(Order.class);
        Page<Order> orderPage = new PageImpl<>(List.of(order), pageable, 1);
        
        given(orderRepository.findMyOrders(memberId, pageable)).willReturn(orderPage);

        // when
        Page<Order> result = orderService.getMyOrders(memberId, pageable);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(orderRepository).findMyOrders(memberId, pageable);
    }

    @Test
    @DisplayName("결제 완료 처리를 성공한다")
    void completePayment_success() {
        // given
        UUID orderId = UUID.randomUUID();
        Order order = spy(Order.register(UUID.randomUUID(), UUID.randomUUID(), 1, BigInteger.valueOf(10000), "테스트"));
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

        // when
        orderService.completePayment(orderId);

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(orderRepository).findById(orderId);
    }

    @Test
    @DisplayName("만료된 주문들을 배치 단위로 자동 만료 처리하고 Redis 재고를 복구한다")
    void processExpiredBatch_success() {
        // given
        LocalDateTime now = LocalDateTime.now();
        int batchSize = 100;
        PageRequest pageRequest = PageRequest.of(0, batchSize);
        UUID courseId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Order expiredOrder = spy(Order.register(memberId, courseId, 1, BigInteger.valueOf(10000), "만료대상"));
        Slice<Order> expiredSlice = new SliceImpl<>(List.of(expiredOrder));
        
        given(orderRepository.findAllByStatusAndExpireAtBefore(eq(OrderStatus.PENDING), eq(now), eq(pageRequest)))
                .willReturn(expiredSlice);

        // when
        OrderService.OrderBatchResult result = orderService.processExpiredBatch(now, batchSize);

        // then
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.fetchedCount()).isEqualTo(1);
        assertThat(expiredOrder.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        
        // Redis 재고 복구 호출 확인
        verify(waitingListService).rollbackOccupiedSeat(courseId, memberId);
    }

    @Test
    @DisplayName("Redis 재고 복구 중 예외가 발생해도 주문 만료 처리는 성공으로 간주한다")
    void processExpiredBatch_success_even_if_redis_fails() {
        // given
        LocalDateTime now = LocalDateTime.now();
        int batchSize = 100;
        UUID courseId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Order expiredOrder = spy(Order.register(memberId, courseId, 1, BigInteger.valueOf(10000), "만료대상"));
        Slice<Order> expiredSlice = new SliceImpl<>(List.of(expiredOrder));
        
        given(orderRepository.findAllByStatusAndExpireAtBefore(any(), any(), any()))
                .willReturn(expiredSlice);
        
        // Redis 복구 시 예외 발생 시뮬레이션
        doThrow(new RuntimeException("Redis connection fail"))
                .when(waitingListService).rollbackOccupiedSeat(any(), any());

        // when
        OrderService.OrderBatchResult result = orderService.processExpiredBatch(now, batchSize);

        // then
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.fetchedCount()).isEqualTo(1);
        assertThat(expiredOrder.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        verify(waitingListService).rollbackOccupiedSeat(courseId, memberId);
    }

    @Test
    @DisplayName("특정 주문의 만료 처리 중 예외 발생 시 해당 건은 제외하고 다음 주문을 처리한다")
    void processExpiredBatch_partial_failure() {
        // given
        LocalDateTime now = LocalDateTime.now();
        int batchSize = 100;
        
        Order normalOrder = spy(Order.register(UUID.randomUUID(), UUID.randomUUID(), 1, BigInteger.valueOf(10000), "정상주문"));
        Order failOrder = spy(Order.register(UUID.randomUUID(), UUID.randomUUID(), 1, BigInteger.valueOf(10000), "실패주문"));
        
        // failOrder.expire() 호출 시 예외 발생 시뮬레이션
        doThrow(new RuntimeException("DB error during expire"))
                .when(failOrder).expire();

        Slice<Order> expiredSlice = new SliceImpl<>(List.of(failOrder, normalOrder));
        
        given(orderRepository.findAllByStatusAndExpireAtBefore(any(), any(), any()))
                .willReturn(expiredSlice);

        // when
        OrderService.OrderBatchResult result = orderService.processExpiredBatch(now, batchSize);

        // then
        assertThat(result.successCount()).isEqualTo(1); // normalOrder만 성공
        assertThat(result.fetchedCount()).isEqualTo(2); // 2건 조회됨
        assertThat(normalOrder.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(failOrder.getStatus()).isEqualTo(OrderStatus.PENDING); // 실패했으므로 상태 유지
    }

    @Test
    @DisplayName("만료된 주문이 없으면 처리 건수가 0이다")
    void processExpiredBatch_empty() {
        // given
        LocalDateTime now = LocalDateTime.now();
        int batchSize = 100;
        PageRequest pageRequest = PageRequest.of(0, batchSize);
        given(orderRepository.findAllByStatusAndExpireAtBefore(eq(OrderStatus.PENDING), eq(now), eq(pageRequest)))
                .willReturn(new SliceImpl<>(Collections.emptyList()));

        // when
        OrderService.OrderBatchResult result = orderService.processExpiredBatch(now, batchSize);

        // then
        assertThat(result.successCount()).isZero();
        assertThat(result.fetchedCount()).isZero();
        verify(waitingListService, never()).rollbackOccupiedSeat(any(), any());
        verify(orderRepository).findAllByStatusAndExpireAtBefore(eq(OrderStatus.PENDING), eq(now), eq(pageRequest));
    }
}
