package four_tential.potential.presentation.payment;

import four_tential.potential.application.payment.PaymentFacade;
import four_tential.potential.application.payment.RefundFacade;
import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.PaymentExceptionEnum;
import four_tential.potential.domain.payment.enums.PaymentPayWay;
import four_tential.potential.domain.payment.enums.PaymentStatus;
import four_tential.potential.infra.security.principal.MemberPrincipal;
import four_tential.potential.presentation.payment.dto.PaymentCreateRequest;
import four_tential.potential.presentation.payment.dto.PaymentCreateResponse;
import four_tential.potential.presentation.payment.dto.RefundPreviewResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @InjectMocks
    private PaymentController paymentController;

    @Mock
    private PaymentFacade paymentFacade;

    @Mock
    private RefundFacade refundFacade;

    @Test
    @DisplayName("createPayment 호출 시 결제 생성 결과를 201 Created 로 반환한다")
    void createPayment_returns_created_response() {
        UUID memberId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        MemberPrincipal principal = new MemberPrincipal(memberId, "student@test.com", "STUDENT");
        PaymentCreateRequest request = new PaymentCreateRequest(
                orderId,
                "pg-key-1",
                PaymentPayWay.CARD,
                null
        );
        PaymentCreateResponse facadeResponse = new PaymentCreateResponse(
                paymentId,
                orderId,
                100000L,
                0L,
                100000L,
                PaymentPayWay.CARD,
                PaymentStatus.PENDING,
                LocalDateTime.of(2026, 4, 17, 10, 0)
        );

        given(paymentFacade.createPayment(memberId, request)).willReturn(facadeResponse);

        ResponseEntity<BaseResponse<PaymentCreateResponse>> response =
                paymentController.createPayment(principal, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().status()).isEqualTo(HttpStatus.CREATED.name());
        assertThat(response.getBody().message()).isEqualTo("결제 요청 성공");
        assertThat(response.getBody().data()).isEqualTo(facadeResponse);
        verify(paymentFacade).createPayment(memberId, request);
    }


    @Test
    @DisplayName("환불 가능이면 200 OK 와 refundable = true 응답을 반환한다")
    void getRefundPreview_returns_200_when_refundable() {
        UUID memberId  = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        MemberPrincipal principal = new MemberPrincipal(memberId, "student@test.com", "STUDENT");
        RefundPreviewResponse preview = new RefundPreviewResponse(
                paymentId, "소도구 필라테스 입문반",
                LocalDateTime.of(2025, 1, 10, 10, 0),
                5, 25000L, 125000L,
                true, "7일 전 취소 · 전액 환불"
        );
        given(refundFacade.getRefundPreview(memberId, paymentId)).willReturn(preview);

        ResponseEntity<BaseResponse<RefundPreviewResponse>> response =
                paymentController.getRefundPreview(principal, paymentId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().message()).isEqualTo("환불 가능 여부 조회 성공");
        RefundPreviewResponse data = response.getBody().data();
        assertThat(data.paymentId()).isEqualTo(paymentId);
        assertThat(data.courseTitle()).isEqualTo("소도구 필라테스 입문반");
        assertThat(data.currentOrderCount()).isEqualTo(5);
        assertThat(data.unitPrice()).isEqualTo(25000L);
        assertThat(data.paidTotalPrice()).isEqualTo(125000L);
        assertThat(data.refundable()).isTrue();
        assertThat(data.refundPolicy()).isEqualTo("7일 전 취소 · 전액 환불");
        verify(refundFacade).getRefundPreview(memberId, paymentId);
    }

    @Test
    @DisplayName("환불 불가이면 200 OK 와 refundable = false 응답을 반환한다")
    void getRefundPreview_returns_200_when_not_refundable() {
        UUID memberId  = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        MemberPrincipal principal = new MemberPrincipal(memberId, "student@test.com", "STUDENT");
        RefundPreviewResponse preview = new RefundPreviewResponse(
                paymentId, "소도구 필라테스 입문반",
                LocalDateTime.of(2025, 1, 10, 10, 0),
                5, 25000L, 125000L,
                false, "7일 이내 취소 · 환불 불가"
        );
        given(refundFacade.getRefundPreview(memberId, paymentId)).willReturn(preview);

        ResponseEntity<BaseResponse<RefundPreviewResponse>> response =
                paymentController.getRefundPreview(principal, paymentId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().refundable()).isFalse();
        assertThat(response.getBody().data().refundPolicy()).isEqualTo("7일 이내 취소 · 환불 불가");
        verify(refundFacade).getRefundPreview(memberId, paymentId);
    }

    @Test
    @DisplayName("결제가 없으면 ServiceErrorException 이 전파된다")
    void getRefundPreview_propagates_exception_when_not_found() {
        UUID memberId  = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        MemberPrincipal principal = new MemberPrincipal(memberId, "student@test.com", "STUDENT");
        given(refundFacade.getRefundPreview(memberId, paymentId))
                .willThrow(new ServiceErrorException(PaymentExceptionEnum.ERR_NOT_FOUND_PAYMENT));

        assertThatThrownBy(() -> paymentController.getRefundPreview(principal, paymentId))
                .isInstanceOf(ServiceErrorException.class);
    }

    @Test
    @DisplayName("환불 불가 결제 상태이면 ServiceErrorException 이 전파된다")
    void getRefundPreview_propagates_exception_when_invalid_status() {
        UUID memberId  = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        MemberPrincipal principal = new MemberPrincipal(memberId, "student@test.com", "STUDENT");
        given(refundFacade.getRefundPreview(memberId, paymentId))
                .willThrow(new ServiceErrorException(PaymentExceptionEnum.ERR_REFUND_NOT_ALLOWED));

        assertThatThrownBy(() -> paymentController.getRefundPreview(principal, paymentId))
                .isInstanceOf(ServiceErrorException.class);
    }
}
