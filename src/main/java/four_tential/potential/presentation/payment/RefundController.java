package four_tential.potential.presentation.payment;

import four_tential.potential.application.payment.RefundFacade;
import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.common.dto.PageResponse;
import four_tential.potential.domain.payment.enums.RefundStatus;
import four_tential.potential.infra.security.principal.MemberPrincipal;
import four_tential.potential.presentation.payment.dto.RefundDetailResponse;
import four_tential.potential.presentation.payment.dto.RefundListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/refunds")
public class RefundController {

    private final RefundFacade refundFacade;

    @GetMapping("/{refundId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<BaseResponse<RefundDetailResponse>> getMyRefund(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable UUID refundId
    ) {
        RefundDetailResponse response = refundFacade.getMyRefund(principal.memberId(), refundId);
        return ResponseEntity.ok(BaseResponse.success(HttpStatus.OK.name(), "환불 조회 성공", response));
    }

    @GetMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<BaseResponse<PageResponse<RefundListResponse>>> getMyRefunds(
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam(required = false) RefundStatus status,
            @PageableDefault Pageable pageable
    ) {
        PageResponse<RefundListResponse> response =
                refundFacade.getAllMyRefunds(principal.memberId(), status, pageable);
        return ResponseEntity.ok(BaseResponse.success(HttpStatus.OK.name(), "환불 목록 조회 성공", response));
    }
}
