package four_tential.potential.presentation.order;

import four_tential.potential.application.order.OrderFacade;
import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.common.dto.PageResponse;
import four_tential.potential.infra.security.principal.MemberPrincipal;
import four_tential.potential.presentation.order.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Order - 주문", description = "주문 생성, 조회, 취소 API")
@RestController
@RequestMapping("/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderFacade orderFacade;

    @Operation(summary = "주문 생성", description = "코스 수강을 위한 주문을 생성합니다. 잔여석이 있으면 즉시 생성되고, 없으면 대기열에 진입합니다.")
    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<BaseResponse<OrderPlaceResult>> createOrder(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody OrderCreateRequest request
    ) {
        OrderPlaceResult result = orderFacade.placeOrder(principal.memberId(), request);

        if (result instanceof OrderCreateResponse createResponse) {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(BaseResponse.success(HttpStatus.CREATED.name(), createResponse.message(), createResponse));
        }

        if (result instanceof OrderWaitingResponse waitingResponse) {
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(BaseResponse.success(HttpStatus.ACCEPTED.name(), waitingResponse.message(), waitingResponse));
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    @Operation(summary = "주문 상세 조회", description = "특정 주문의 상세 정보를 조회합니다.")
    @GetMapping("/{orderId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<BaseResponse<OrderDetailResponse>> getOrderDetails(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable UUID orderId
    ) {
        OrderDetailResponse response = orderFacade.getOrderDetails(orderId, principal.memberId());
        return ResponseEntity.ok(BaseResponse.success(HttpStatus.OK.name(), "주문 상세 조회 성공", response));
    }

    @Operation(summary = "나의 주문 목록 조회", description = "로그인한 회원의 주문 내역을 페이징하여 조회합니다.")
    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<BaseResponse<PageResponse<OrderMyListResponse>>> getMyOrders(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PageableDefault(size = 10) Pageable pageable
    ) {
        PageResponse<OrderMyListResponse> response = orderFacade.getMyOrders(principal.memberId(), pageable);
        return ResponseEntity.ok(BaseResponse.success(HttpStatus.OK.name(), "나의 주문 목록 조회 성공", response));
    }

    @Operation(summary = "주문 취소", description = "본인의 주문을 취소합니다. 결제 전(PENDING)이면 즉시 취소되고, 결제 후(PAID)이면 환불 정책에 따라 취소됩니다.")
    @PatchMapping("/{orderId}/cancel")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<BaseResponse<OrderCancelResponse>> cancelOrder(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable UUID orderId,
            @Valid @RequestBody OrderCancelRequest request
    ) {
        OrderCancelResponse response = orderFacade.cancelOrder(orderId, principal.memberId(), request);
        return ResponseEntity.ok(BaseResponse.success(HttpStatus.OK.name(), "주문 취소 요청 성공", response));
    }

}
