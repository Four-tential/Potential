package four_tential.potential.presentation.order;

import four_tential.potential.application.order.OrderService;
import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.domain.order.OrderStatus;
import four_tential.potential.presentation.order.dto.OrderAdminStatusUpdateRequest;
import four_tential.potential.presentation.order.dto.OrderAdminStatusUpdateResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminOrderControllerTest {

    @Mock
    private OrderService orderService;

    @InjectMocks
    private AdminOrderController adminOrderController;

    @Test
    @DisplayName("관리자 주문 상태 강제 변경 성공 시 200 OK와 변경 정보를 반환한다")
    void updateOrderStatus_Success() {
        // given
        UUID orderId = UUID.randomUUID();
        OrderAdminStatusUpdateRequest request = new OrderAdminStatusUpdateRequest(OrderStatus.PAID, "입금 확인 후 수동 승인");
        OrderAdminStatusUpdateResponse expectedResponse = new OrderAdminStatusUpdateResponse(orderId, OrderStatus.PENDING, OrderStatus.PAID);

        given(orderService.updateOrderStatusByAdmin(eq(orderId), eq(request)))
                .willReturn(expectedResponse);

        // when
        ResponseEntity<BaseResponse<OrderAdminStatusUpdateResponse>> response = adminOrderController.updateOrderStatus(orderId, request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(HttpStatus.OK.name());
        assertThat(response.getBody().message()).isEqualTo("관리자 주문 상태 변경 성공");
        assertThat(response.getBody().data()).isEqualTo(expectedResponse);

        verify(orderService).updateOrderStatusByAdmin(orderId, request);
    }
}
