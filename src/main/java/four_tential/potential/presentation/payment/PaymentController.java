package four_tential.potential.presentation.payment;

import four_tential.potential.application.payment.PaymentFacade;
import four_tential.potential.application.payment.RefundFacade;
import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.common.dto.PageResponse;
import four_tential.potential.domain.payment.enums.PaymentStatus;
import four_tential.potential.infra.security.principal.MemberPrincipal;
import four_tential.potential.presentation.payment.dto.PaymentCreateRequest;
import four_tential.potential.presentation.payment.dto.PaymentCreateResponse;
import four_tential.potential.presentation.payment.dto.PaymentDetailResponse;
import four_tential.potential.presentation.payment.dto.PaymentListResponse;
import four_tential.potential.presentation.payment.dto.RefundPreviewResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "결제", description = "결제 준비 · 결제 조회 · 환불 가능 여부 미리보기 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/payments")
public class PaymentController {

    private final PaymentFacade paymentFacade;
    private final RefundFacade refundFacade;

    @Operation(
            summary = "결제 준비",
            description = """
                    학생이 선택한 주문에 대해 결제 준비를 생성합니다.

                    - 같은 주문에 대해 orderId lock으로 중복 결제 준비를 막습니다.
                    - 서버가 pgKey를 발급하고 payments row를 PENDING 상태로 먼저 저장합니다.
                    - 이미 같은 주문의 PENDING payment가 있으면 새 결제 건을 만들지 않고 기존 pgKey를 반환합니다.
                    - 실제 결제 확정은 클라이언트 응답이 아니라 Paid 웹훅 검증 이후에 진행됩니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "결제 준비 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청값(orderId, payWay 등)"),
            @ApiResponse(responseCode = "403", description = "학생 권한이 없거나 본인 주문이 아님"),
            @ApiResponse(responseCode = "404", description = "주문 또는 코스를 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "결제 준비 불가(이미 결제됨, 결제 만료, 좌석 부족 등)")
    })
    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<BaseResponse<PaymentCreateResponse>> createPayment(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody PaymentCreateRequest request
    ) {
        PaymentCreateResponse response = paymentFacade.createPayment(principal.memberId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success(HttpStatus.CREATED.name(), "결제 준비 성공", response));
    }

    @Operation(
            summary = "내 결제 상세 조회",
            description = """
                    학생 본인의 결제 상세 정보를 조회합니다.

                    - 본인 결제 건만 조회할 수 있습니다.
                    - 결제 수단, 결제 상태, 주문 연결 정보 등을 함께 반환합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "결제 상세 조회 성공"),
            @ApiResponse(responseCode = "403", description = "학생 권한이 없거나 본인 결제가 아님"),
            @ApiResponse(responseCode = "404", description = "결제 정보를 찾을 수 없음")
    })
    @GetMapping("/{paymentId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<BaseResponse<PaymentDetailResponse>> getMyPayment(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Parameter(description = "조회할 결제 ID", required = true)
            @PathVariable UUID paymentId
    ) {
        PaymentDetailResponse response = paymentFacade.getMyPayment(principal.memberId(), paymentId);
        return ResponseEntity.ok(BaseResponse.success(HttpStatus.OK.name(), "결제 조회 성공", response));
    }

    @Operation(
            summary = "내 결제 목록 조회",
            description = """
                    학생 본인의 결제 목록을 페이지 단위로 조회합니다.

                    - status 파라미터를 주면 특정 결제 상태만 필터링할 수 있습니다.
                    - pageable 기준으로 페이지 번호, 크기, 정렬을 함께 적용합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "결제 목록 조회 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 상태값 또는 페이지 요청"),
            @ApiResponse(responseCode = "403", description = "학생 권한이 없음")
    })
    @GetMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<BaseResponse<PageResponse<PaymentListResponse>>> getMyPayments(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Parameter(description = "조회할 결제 상태 (PENDING, PAID, PART_REFUNDED, REFUNDED, FAILED)")
            @RequestParam(required = false) PaymentStatus status,
            @PageableDefault Pageable pageable
    ) {
        PageResponse<PaymentListResponse> response = paymentFacade.getAllMyPayments(
                principal.memberId(), status, pageable);
        return ResponseEntity.ok(BaseResponse.success(HttpStatus.OK.name(), "결제 목록 조회 성공", response));
    }

    @Operation(
            summary = "환불 가능 여부 미리보기",
            description = """
                    실제 환불을 실행하지 않고, 해당 결제 건의 환불 가능 여부를 미리 조회합니다.

                    - 본인 결제 건만 조회할 수 있습니다.
                    - 현재 남은 수강권 수량, 1회 가격, 결제 총액, 환불 정책 문구를 반환합니다.
                    - 환불 가능 상태(PAID, PART_REFUNDED)와 환불 기간 조건을 기준으로 계산합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "환불 가능 여부 조회 성공"),
            @ApiResponse(responseCode = "403", description = "학생 권한이 없거나 본인 결제가 아님"),
            @ApiResponse(responseCode = "404", description = "결제 정보를 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "환불 가능한 결제 상태가 아님")
    })
    @GetMapping("/{paymentId}/refund-preview")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<BaseResponse<RefundPreviewResponse>> getRefundPreview(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Parameter(description = "환불 가능 여부를 확인할 결제 ID", required = true)
            @PathVariable UUID paymentId
    ) {
        RefundPreviewResponse response = refundFacade.getRefundPreview(principal.memberId(), paymentId);
        return ResponseEntity.ok(BaseResponse.success(HttpStatus.OK.name(), "환불 가능 여부 조회 성공", response));
    }
}
