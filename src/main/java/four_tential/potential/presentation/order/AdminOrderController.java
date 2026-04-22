package four_tential.potential.presentation.order;

import four_tential.potential.application.order.OrderService;
import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.presentation.order.dto.OrderAdminStatusUpdateRequest;
import four_tential.potential.presentation.order.dto.OrderAdminStatusUpdateResponse;
import four_tential.potential.presentation.order.dto.OrderInventoryReconcileResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderService orderService;

    /**
     * 관리자 주문 상태 강제 변경
     */
    @PatchMapping("/{orderId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BaseResponse<OrderAdminStatusUpdateResponse>> updateOrderStatus(
            @PathVariable UUID orderId,
            @Valid @RequestBody OrderAdminStatusUpdateRequest request
    ) {
        OrderAdminStatusUpdateResponse response = orderService.updateOrderStatusByAdmin(orderId, request);
        return ResponseEntity.ok(BaseResponse.success(HttpStatus.OK.name(), "관리자 주문 상태 변경 성공", response));
    }

    /**
     * 특정 코스의 재고 정합성 복구 (Admin 전용)
     */
    @PostMapping("/inventory/reconcile")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BaseResponse<OrderInventoryReconcileResponse>> reconcileInventory(
            @RequestParam UUID courseId
    ) {
        OrderInventoryReconcileResponse response = orderService.reconcileInventory(courseId);
        return ResponseEntity.ok(BaseResponse.success(HttpStatus.OK.name(), "재고 정합성 복구 성공", response));
    }
}
