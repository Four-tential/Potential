package four_tential.potential.application.order;

import four_tential.potential.domain.order.Order;
import four_tential.potential.domain.order.OrderStatus;
import four_tential.potential.presentation.order.dto.OrderCreateRequest;
import four_tential.potential.presentation.order.dto.OrderCreateResponse;
import four_tential.potential.presentation.order.dto.OrderPlaceResult;
import four_tential.potential.presentation.order.dto.OrderWaitingResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderFacadeTest {

    @Mock private OrderService orderService;
    @Mock private WaitingListService waitingListService;

    @InjectMocks private OrderFacade orderFacade;

    private final UUID memberId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();
    private final OrderCreateRequest request = new OrderCreateRequest(
            courseId, 2, BigInteger.valueOf(50000), "테스트 강의"
    );

    @Test
    @DisplayName("잔여석 점유 성공 시 주문을 생성하고 성공 응답을 반환한다")
    void placeOrder_success_occupy_seat() {
        // given
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
        verify(orderService).createOrder(memberId, request);
    }

    @Test
    @DisplayName("잔여석 점유 실패 시 대기열에 추가한다")
    void placeOrder_fail_to_waiting() {
        // given
        given(waitingListService.tryOccupyingSeat(courseId, memberId, 2)).willReturn(false);

        // when
        OrderPlaceResult result = orderFacade.placeOrder(memberId, request);

        // then
        assertThat(result).isInstanceOf(OrderWaitingResponse.class);
        verify(waitingListService).addToWaitingList(courseId, memberId);
    }

    @Test
    @DisplayName("주문 DB 저장 실패 시 점유된 잔여석을 롤백한다")
    void placeOrder_rollback_when_db_fails() {
        // given
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
}
