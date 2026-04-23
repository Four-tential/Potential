package four_tential.potential.application.order;

import four_tential.potential.application.payment.RefundFacade;
import four_tential.potential.common.dto.PageResponse;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.OrderExceptionEnum;
import four_tential.potential.domain.order.Order;
import four_tential.potential.domain.order.OrderStatus;
import four_tential.potential.presentation.order.dto.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderFacadeTest {

    @Mock private OrderService orderService;
    @Mock private WaitingListService waitingListService;
    @Mock private RefundFacade refundFacade;

    @InjectMocks private OrderFacade orderFacade;

    private final UUID memberId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();
    private final OrderCreateRequest request = new OrderCreateRequest(courseId, 2);

    @Test
    @DisplayName("잔여석 점유 성공 시 주문을 생성하고 성공 응답을 반환한다")
    void placeOrder_success_occupy_seat() {
        // given
        doNothing().when(orderService).checkDuplicateTimeCourse(memberId, courseId);
        doNothing().when(orderService).reconcileInventoryIfNecessary(courseId);
        given(waitingListService.tryOccupyingSeat(courseId, memberId, 2)).willReturn(true);
        
        Order order = mock(Order.class);
        given(order.getId()).willReturn(UUID.randomUUID());
        given(order.getStatus()).willReturn(OrderStatus.PENDING);
        given(order.getExpireAt()).willReturn(LocalDateTime.now().plusMinutes(10));
        
        given(orderService.createOrder(memberId, request)).willReturn(order);

        // when
        OrderPlaceResult result = orderFacade.placeOrder(memberId, request);

        // then
        assertThat(result).isInstanceOf(OrderCreateResponse.class);
        verify(orderService).checkDuplicateTimeCourse(memberId, courseId);
        verify(orderService).reconcileInventoryIfNecessary(courseId);
        verify(orderService).createOrder(memberId, request);
    }

    @Test
    @DisplayName("중복 시간대에 이미 예약이 있는 경우 대기열 진입 시도조차 하지 않고 예외를 발생시킨다")
    void placeOrder_fail_duplicate_time() {
        // given
        doThrow(new ServiceErrorException(OrderExceptionEnum.ERR_ALREADY_RESERVED))
                .when(orderService).checkDuplicateTimeCourse(memberId, courseId);

        // when & then
        assertThatThrownBy(() -> orderFacade.placeOrder(memberId, request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(OrderExceptionEnum.ERR_ALREADY_RESERVED.getMessage());

        verify(orderService).checkDuplicateTimeCourse(memberId, courseId);
        verify(orderService, never()).reconcileInventoryIfNecessary(any());
        verify(waitingListService, never()).tryOccupyingSeat(any(), any(), anyInt());
        verify(waitingListService, never()).addToWaitingList(any(), any());
    }

    @Test
    @DisplayName("잔여석 점유 실패 시 대기열에 추가하기 전 중복 체크를 먼저 수행한다")
    void placeOrder_fail_to_waiting() {
        // given
        doNothing().when(orderService).checkDuplicateTimeCourse(memberId, courseId);
        doNothing().when(orderService).reconcileInventoryIfNecessary(courseId);
        given(waitingListService.tryOccupyingSeat(courseId, memberId, 2)).willReturn(false);

        // when
        OrderPlaceResult result = orderFacade.placeOrder(memberId, request);

        // then
        assertThat(result).isInstanceOf(OrderWaitingResponse.class);
        verify(orderService).checkDuplicateTimeCourse(memberId, courseId);
        verify(orderService).reconcileInventoryIfNecessary(courseId);
        verify(waitingListService).addToWaitingList(courseId, memberId);
    }

    @Test
    @DisplayName("주문 DB 저장 실패 시 점유된 잔여석을 롤백한다")
    void placeOrder_rollback_when_db_fails() {
        // given
        doNothing().when(orderService).reconcileInventoryIfNecessary(courseId);
        given(waitingListService.tryOccupyingSeat(courseId, memberId, 2)).willReturn(true);
        given(orderService.createOrder(memberId, request))
                .willThrow(new RuntimeException("DB 저장 오류"));

        // when & then
        assertThatThrownBy(() -> orderFacade.placeOrder(memberId, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB 저장 오류");

        // 보상 트랜잭션 검증: rollbackOccupiedSeat이 호출되어야 함
        verify(waitingListService).rollbackOccupiedSeat(courseId, memberId);
    }

    @Test
    @DisplayName("보상 트랜잭션 중 발생한 예외는 원래 예외에 suppressed 된다")
    void placeOrder_rollback_suppress_exception() {
        // given
        doNothing().when(orderService).reconcileInventoryIfNecessary(courseId);
        given(waitingListService.tryOccupyingSeat(courseId, memberId, 2)).willReturn(true);
        given(orderService.createOrder(memberId, request))
                .willThrow(new RuntimeException("DB 저장 오류"));
        
        // 롤백 중에도 예외 발생
        doThrow(new RuntimeException("롤백 실패 오류"))
                .when(waitingListService).rollbackOccupiedSeat(courseId, memberId);

        // when & then
        assertThatThrownBy(() -> orderFacade.placeOrder(memberId, request))
                .isInstanceOf(RuntimeException.class)
                .satisfies(e -> {
                    assertThat(e.getMessage()).isEqualTo("DB 저장 오류");
                    assertThat(e.getSuppressed()).hasSize(1);
                    assertThat(e.getSuppressed()[0].getMessage()).isEqualTo("롤백 실패 오류");
                });
    }

    @Test
    @DisplayName("주문 상세 조회를 성공적으로 수행한다")
    void getOrderDetails_success() {
        // given
        UUID orderId = UUID.randomUUID();
        Order order = mock(Order.class);
        given(order.getId()).willReturn(orderId);
        given(order.getCourseId()).willReturn(courseId);
        given(order.getTitleSnap()).willReturn("테스트 강의");
        given(order.getOrderCount()).willReturn(2);
        given(order.getPriceSnap()).willReturn(BigInteger.valueOf(50000));
        given(order.getTotalPriceSnap()).willReturn(BigInteger.valueOf(100000));
        given(order.getStatus()).willReturn(OrderStatus.PENDING);
        given(order.getCreatedAt()).willReturn(LocalDateTime.now());
        given(order.getUpdatedAt()).willReturn(LocalDateTime.now());
        given(order.getExpireAt()).willReturn(LocalDateTime.now().plusMinutes(10));

        given(orderService.getOrderDetails(orderId, memberId)).willReturn(order);

        // when
        OrderDetailResponse result = orderFacade.getOrderDetails(orderId, memberId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.orderId()).isEqualTo(orderId);
        verify(orderService).getOrderDetails(orderId, memberId);
    }

    @Test
    @DisplayName("나의 주문 목록 조회를 성공적으로 수행한다")
    void getMyOrders_success() {
        // given
        PageRequest pageRequest = PageRequest.of(0, 10);
        Order order = mock(Order.class);
        given(order.getId()).willReturn(UUID.randomUUID());
        given(order.getCourseId()).willReturn(courseId);
        given(order.getTitleSnap()).willReturn("테스트 강의");
        given(order.getTotalPriceSnap()).willReturn(BigInteger.valueOf(100000));
        given(order.getStatus()).willReturn(OrderStatus.PAID);
        given(order.getCreatedAt()).willReturn(LocalDateTime.now());
        given(order.getUpdatedAt()).willReturn(LocalDateTime.now());
        given(order.getExpireAt()).willReturn(LocalDateTime.now().plusMinutes(10));

        Page<Order> orderPage = new PageImpl<>(List.of(order), pageRequest, 1);
        given(orderService.getMyOrders(memberId, pageRequest)).willReturn(orderPage);

        // when
        PageResponse<OrderMyListResponse> result = orderFacade.getMyOrders(memberId, pageRequest);

        // then
        assertThat(result).isNotNull();
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).titleSnap()).isEqualTo("테스트 강의");
        assertThat(result.currentPage()).isEqualTo(0);
        assertThat(result.totalPages()).isEqualTo(1);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.isLast()).isTrue();
        verify(orderService).getMyOrders(memberId, pageRequest);
    }

    @Test
    @DisplayName("결제 전 주문을 취소하면 주문 상태를 변경하고 Redis 재고를 복구한다")
    void cancelOrder_success() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        LocalDateTime cancelledAt = LocalDateTime.now();
        OrderCancelRequest request = new OrderCancelRequest(2);
        
        Order pendingOrder = mock(Order.class);
        given(pendingOrder.getOrderCount()).willReturn(2);
        given(pendingOrder.getStatus()).willReturn(OrderStatus.PENDING);

        Order order = mock(Order.class);
        given(order.getId()).willReturn(orderId);
        given(order.getMemberId()).willReturn(memberId);
        given(order.getCourseId()).willReturn(courseId);
        given(order.getOrderCount()).willReturn(2);
        given(order.getStatus()).willReturn(OrderStatus.CANCELLED);
        given(order.getCancelledAt()).willReturn(cancelledAt);
        
        given(orderService.getOrderDetails(orderId, memberId)).willReturn(pendingOrder);
        given(orderService.cancelOrder(orderId, memberId)).willReturn(order);

        // when
        OrderCancelResponse response = orderFacade.cancelOrder(orderId, memberId, request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(response.cancelledAt()).isEqualTo(cancelledAt);
        verify(orderService).cancelOrder(orderId, memberId);
        verify(waitingListService).recoverCapacity(courseId, memberId, 2);
        verify(refundFacade, never()).refundPaidOrderByStudent(any(), any(), anyInt());
    }

    @Test
    @DisplayName("결제 완료 주문 취소는 환불 흐름을 먼저 호출한다")
    void cancelOrder_paid_order_refunds_first() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        LocalDateTime cancelledAt = LocalDateTime.now();
        OrderCancelRequest request = new OrderCancelRequest(1);

        Order paidOrder = mock(Order.class);
        given(paidOrder.getStatus()).willReturn(OrderStatus.PAID);

        Order refundedOrder = mock(Order.class);
        given(refundedOrder.getId()).willReturn(orderId);
        given(refundedOrder.getStatus()).willReturn(OrderStatus.CANCELLED);
        given(refundedOrder.getCancelledAt()).willReturn(cancelledAt);

        given(orderService.getOrderDetails(orderId, memberId)).willReturn(paidOrder, refundedOrder);

        // when
        OrderCancelResponse response = orderFacade.cancelOrder(orderId, memberId, request);

        // then
        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.status()).isEqualTo("CANCELLED");
        verify(refundFacade).refundPaidOrderByStudent(memberId, orderId, 1);
        verify(orderService, never()).cancelOrder(orderId, memberId);
        verify(waitingListService, never()).recoverCapacity(any(), any(), anyInt());
    }
}
