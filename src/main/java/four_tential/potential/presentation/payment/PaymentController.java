package four_tential.potential.presentation.payment;

import four_tential.potential.application.payment.PaymentFacade;
import four_tential.potential.application.payment.RefundFacade;
import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.infra.security.principal.MemberPrincipal;
import four_tential.potential.presentation.payment.dto.PaymentCreateRequest;
import four_tential.potential.presentation.payment.dto.PaymentCreateResponse;
import four_tential.potential.presentation.payment.dto.RefundPreviewResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/v1/payments", "/api/v1/payments"})
public class PaymentController {

    private final PaymentFacade paymentFacade;
    private final RefundFacade refundFacade;

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

    @GetMapping("/{paymentId}/refund-preview")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<BaseResponse<RefundPreviewResponse>> getRefundPreview(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable UUID paymentId
    ) {
        RefundPreviewResponse response = refundFacade.getRefundPreview(principal.memberId(), paymentId);
        return ResponseEntity.ok(BaseResponse.success(HttpStatus.OK.name(), "환불 가능 여부 조회 성공", response));
    }
}
