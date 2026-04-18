package four_tential.potential.presentation.payment;

import four_tential.potential.application.payment.PaymentFacade;
import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.common.dto.PageResponse;
import four_tential.potential.domain.payment.enums.PaymentStatus;
import four_tential.potential.infra.security.principal.MemberPrincipal;
import four_tential.potential.presentation.payment.dto.PaymentCreateRequest;
import four_tential.potential.presentation.payment.dto.PaymentCreateResponse;
import four_tential.potential.presentation.payment.dto.PaymentDetailResponse;
import four_tential.potential.presentation.payment.dto.PaymentListResponse;
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

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/payments")
public class PaymentController {

    private final PaymentFacade paymentFacade;

    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<BaseResponse<PaymentCreateResponse>> createPayment(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody PaymentCreateRequest request
    ) {
        PaymentCreateResponse response = paymentFacade.createPayment(principal.memberId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success(HttpStatus.CREATED.name(), "결제 요청 성공", response));
    }

    @GetMapping("/{paymentId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<BaseResponse<PaymentDetailResponse>> getMyPayment(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable UUID paymentId
    ) {
        PaymentDetailResponse response = paymentFacade.getMyPayment(principal.memberId(), paymentId);
        return ResponseEntity.ok(BaseResponse.success(HttpStatus.OK.name(), "결제 조회 성공", response));
    }

    @GetMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<BaseResponse<PageResponse<PaymentListResponse>>> getMyPayments(
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam(required = false) PaymentStatus status,
            @PageableDefault Pageable pageable
    ) {
        PageResponse<PaymentListResponse> response = paymentFacade.getAllMyPayments(
                principal.memberId(), status, pageable);
        return ResponseEntity.ok(BaseResponse.success(HttpStatus.OK.name(), "결제 목록 조회 성공", response));
    }
}
