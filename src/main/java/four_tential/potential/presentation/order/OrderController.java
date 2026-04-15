package four_tential.potential.presentation.order;

import four_tential.potential.application.order.OrderFacade;
import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.infra.security.principal.MemberPrincipal;
import four_tential.potential.presentation.order.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderFacade orderFacade;

    /**
     * 주문 생성
     */
    @PostMapping
    @PreAuthorize("hasRole('ROLE_STUDENT')")
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

    /**
     * 주문 상세 조회
     */
    @GetMapping("/{orderId}")
    @PreAuthorize("hasRole('ROLE_STUDENT')")
    public ResponseEntity<BaseResponse<OrderDetailResponse>> getOrderDetails(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable UUID orderId
    ) {
        OrderDetailResponse response = orderFacade.getOrderDetails(orderId, principal.memberId());
        return ResponseEntity.ok(BaseResponse.success(HttpStatus.OK.name(), "주문 상세 조회 성공", response));
    }

}
