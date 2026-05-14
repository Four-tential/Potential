package four_tential.potential.presentation.order;

import four_tential.potential.application.order.OrderService;
import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.presentation.order.dto.OrderAdminStatusUpdateRequest;
import four_tential.potential.presentation.order.dto.OrderAdminStatusUpdateResponse;
import four_tential.potential.presentation.order.dto.OrderInventoryReconcileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Order - 관리자 주문", description = "관리자용 주문 관리 및 재고 정합성 복구 API")
@RestController
@RequestMapping("/v1/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderService orderService;

    @Operation(summary = "관리자 주문 상태 강제 변경", description = "관리자가 특정 주문의 상태를 강제로 변경합니다.")
    @PatchMapping("/{orderId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BaseResponse<OrderAdminStatusUpdateResponse>> updateOrderStatus(
            @PathVariable UUID orderId,
            @Valid @RequestBody OrderAdminStatusUpdateRequest request
    ) {
        OrderAdminStatusUpdateResponse response = orderService.updateOrderStatusByAdmin(orderId, request);
        return ResponseEntity.ok(BaseResponse.success(HttpStatus.OK.name(), "관리자 주문 상태 변경 성공", response));
    }

    @Operation(summary = "특정 코스의 재고 정합성 복구", description = "DB의 유효 주문 수와 Redis의 재고 수치를 대조하여 불일치 시 Redis 재고를 강제 동기화합니다.")
    @PostMapping("/inventory/reconcile")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BaseResponse<OrderInventoryReconcileResponse>> reconcileInventory(
            @RequestParam UUID courseId
    ) {
        OrderInventoryReconcileResponse response = orderService.reconcileInventory(courseId);
        return ResponseEntity.ok(BaseResponse.success(HttpStatus.OK.name(), "재고 정합성 복구 성공", response));
    }
}
