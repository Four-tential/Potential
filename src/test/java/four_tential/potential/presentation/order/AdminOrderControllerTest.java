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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AdminOrderControllerTest {

    @Mock
    private OrderService orderService;

    @InjectMocks
    private AdminOrderController adminOrderController;

    private static final UUID TARGET_ORDER_ID = UUID.randomUUID();

    @Test
    @DisplayName("관리자 주문 상태 변경 - 200 OK 및 변경된 정보 반환 (PENDING → PAID)")
    void updateOrderStatus_pendingToPaid_success() {
        // given
        OrderAdminStatusUpdateRequest request = new OrderAdminStatusUpdateRequest(OrderStatus.PAID, "입금 확인 완료");
        OrderAdminStatusUpdateResponse serviceResponse = new OrderAdminStatusUpdateResponse(
                TARGET_ORDER_ID,
                OrderStatus.PENDING,
                OrderStatus.PAID
        );
        given(orderService.updateOrderStatusByAdmin(eq(TARGET_ORDER_ID), any(OrderAdminStatusUpdateRequest.class)))
                .willReturn(serviceResponse);

        // when
        ResponseEntity<BaseResponse<OrderAdminStatusUpdateResponse>> response = 
                adminOrderController.updateOrderStatus(TARGET_ORDER_ID, request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("OK");
        assertThat(response.getBody().message()).isEqualTo("관리자 주문 상태 변경 성공");
        assertThat(response.getBody().data().orderId()).isEqualTo(TARGET_ORDER_ID);
        assertThat(response.getBody().data().currentStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(response.getBody().data().previousStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("관리자 주문 상태 변경 - 200 OK 및 변경된 정보 반환 (PAID → CANCELLED)")
    void updateOrderStatus_paidToCancelled_success() {
        // given
        OrderAdminStatusUpdateRequest request = new OrderAdminStatusUpdateRequest(OrderStatus.CANCELLED, "관리자 강제 취소");
        OrderAdminStatusUpdateResponse serviceResponse = new OrderAdminStatusUpdateResponse(
                TARGET_ORDER_ID,
                OrderStatus.PAID,
                OrderStatus.CANCELLED
        );
        given(orderService.updateOrderStatusByAdmin(eq(TARGET_ORDER_ID), any(OrderAdminStatusUpdateRequest.class)))
                .willReturn(serviceResponse);

        // when
        ResponseEntity<BaseResponse<OrderAdminStatusUpdateResponse>> response = 
                adminOrderController.updateOrderStatus(TARGET_ORDER_ID, request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().currentStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(response.getBody().data().previousStatus()).isEqualTo(OrderStatus.PAID);
    }
}
