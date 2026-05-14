package four_tential.potential.presentation.payment;

import four_tential.potential.application.payment.RefundFacade;
import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.common.dto.PageResponse;
import four_tential.potential.domain.payment.enums.RefundStatus;
import four_tential.potential.infra.security.principal.MemberPrincipal;
import four_tential.potential.presentation.payment.dto.RefundDetailResponse;
import four_tential.potential.presentation.payment.dto.RefundListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "환불", description = "환불 상세 조회 · 환불 목록 조회 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/refunds")
public class RefundController {

    private final RefundFacade refundFacade;

    @Operation(
            summary = "내 환불 상세 조회",
            description = """
                    학생 본인의 환불 상세 정보를 조회합니다.

                    - 본인 환불 건만 조회할 수 있습니다.
                    - 부분 환불, 전액 환불, 일괄 환불 모두 refund 단위로 상세 이력을 확인할 수 있습니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "환불 상세 조회 성공"),
            @ApiResponse(responseCode = "403", description = "학생 권한이 없거나 본인 환불 건이 아님"),
            @ApiResponse(responseCode = "404", description = "환불 정보를 찾을 수 없음")
    })
    @GetMapping("/{refundId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<BaseResponse<RefundDetailResponse>> getMyRefund(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Parameter(description = "조회할 환불 ID", required = true)
            @PathVariable UUID refundId
    ) {
        RefundDetailResponse response = refundFacade.getMyRefund(principal.memberId(), refundId);
        return ResponseEntity.ok(BaseResponse.success(HttpStatus.OK.name(), "환불 조회 성공", response));
    }

    @Operation(
            summary = "내 환불 목록 조회",
            description = """
                    학생 본인의 환불 목록을 페이지 단위로 조회합니다.

                    - status 파라미터를 주면 COMPLETED, FAILED 상태별로 필터링할 수 있습니다.
                    - pageable 기준으로 페이지 번호, 크기, 정렬을 함께 적용합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "환불 목록 조회 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 상태값 또는 페이지 요청"),
            @ApiResponse(responseCode = "403", description = "학생 권한이 없음")
    })
    @GetMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<BaseResponse<PageResponse<RefundListResponse>>> getMyRefunds(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Parameter(description = "조회할 환불 상태 (COMPLETED, FAILED)")
            @RequestParam(required = false) RefundStatus status,
            @PageableDefault Pageable pageable
    ) {
        PageResponse<RefundListResponse> response =
                refundFacade.getAllMyRefunds(principal.memberId(), status, pageable);
        return ResponseEntity.ok(BaseResponse.success(HttpStatus.OK.name(), "환불 목록 조회 성공", response));
    }
}
