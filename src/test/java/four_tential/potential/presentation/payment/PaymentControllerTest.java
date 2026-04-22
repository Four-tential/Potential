package four_tential.potential.presentation.payment;

import four_tential.potential.application.payment.PaymentFacade;
import four_tential.potential.application.payment.RefundFacade;
import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.common.dto.PageResponse;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.PaymentExceptionEnum;
import four_tential.potential.domain.payment.enums.PaymentPayWay;
import four_tential.potential.domain.payment.enums.PaymentStatus;
import four_tential.potential.infra.security.principal.MemberPrincipal;
import four_tential.potential.presentation.payment.dto.PaymentCreateRequest;
import four_tential.potential.presentation.payment.dto.PaymentCreateResponse;
import four_tential.potential.presentation.payment.dto.PaymentDetailResponse;
import four_tential.potential.presentation.payment.dto.PaymentListResponse;
import four_tential.potential.presentation.payment.dto.RefundPreviewResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
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
    @DisplayName("createPayment 호출 시 결제 준비 결과를 201 Created로 반환한다")
    void createPayment_returns_created_response() {
        UUID memberId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        MemberPrincipal principal = new MemberPrincipal(memberId, "student@test.com", "STUDENT");
        PaymentCreateRequest request = new PaymentCreateRequest(orderId, PaymentPayWay.CARD);
        PaymentCreateResponse facadeResponse = new PaymentCreateResponse(
                paymentId,
                orderId,
                "pservergeneratedkey",
                100000L,
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
        assertThat(response.getBody().message()).isEqualTo("결제 준비 성공");
        assertThat(response.getBody().data()).isEqualTo(facadeResponse);
        assertThat(response.getBody().data().pgKey()).isEqualTo("pservergeneratedkey");
        verify(paymentFacade).createPayment(memberId, request);
    }

    @Test
    @DisplayName("본인 결제면 200 OK와 PaymentDetailResponse를 반환한다")
    void getMyPayment_returns_200_with_detail() {
        UUID memberId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        MemberPrincipal principal = new MemberPrincipal(memberId, "student@test.com", "STUDENT");
        PaymentDetailResponse detail = new PaymentDetailResponse(
                paymentId,
                orderId,
                "소도구 필라테스 입문반",
                5,
                125000L,
                125000L,
                PaymentPayWay.CARD,
                PaymentStatus.PAID,
                LocalDateTime.of(2025, 1, 1, 10, 0)
        );
        given(paymentFacade.getMyPayment(memberId, paymentId)).willReturn(detail);

        ResponseEntity<BaseResponse<PaymentDetailResponse>> response =
                paymentController.getMyPayment(principal, paymentId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().status()).isEqualTo(HttpStatus.OK.name());
        assertThat(response.getBody().message()).isEqualTo("결제 조회 성공");
        assertThat(response.getBody().data()).isEqualTo(detail);
        verify(paymentFacade).getMyPayment(memberId, paymentId);
    }

    @Test
    @DisplayName("결제가 없으면 ServiceErrorException을 전파한다")
    void getMyPayment_propagates_exception_when_not_found() {
        UUID memberId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        MemberPrincipal principal = new MemberPrincipal(memberId, "student@test.com", "STUDENT");
        given(paymentFacade.getMyPayment(memberId, paymentId))
                .willThrow(new ServiceErrorException(PaymentExceptionEnum.ERR_NOT_FOUND_PAYMENT));

        assertThatThrownBy(() -> paymentController.getMyPayment(principal, paymentId))
                .isInstanceOf(ServiceErrorException.class);
    }

    @Test
    @DisplayName("status가 null이면 전체 결제 목록을 200 OK로 반환한다")
    void getMyPayments_returns_200_with_all_when_status_null() {
        UUID memberId = UUID.randomUUID();
        MemberPrincipal principal = new MemberPrincipal(memberId, "student@test.com", "STUDENT");
        Pageable pageable = PageRequest.of(0, 10);
        PageResponse<PaymentListResponse> pageResponse = new PageResponse<>(
                List.of(new PaymentListResponse(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "소도구 필라테스 입문반",
                        5,
                        125000L,
                        PaymentStatus.PAID,
                        LocalDateTime.of(2025, 1, 1, 10, 0)
                )),
                0,
                1,
                1L,
                10,
                true
        );
        given(paymentFacade.getAllMyPayments(memberId, null, pageable)).willReturn(pageResponse);

        ResponseEntity<BaseResponse<PageResponse<PaymentListResponse>>> response =
                paymentController.getMyPayments(principal, null, pageable);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().message()).isEqualTo("결제 목록 조회 성공");
        assertThat(response.getBody().data()).isEqualTo(pageResponse);
    }

    @Test
    @DisplayName("status가 PAID면 PAID 결제 목록만 반환한다")
    void getMyPayments_returns_filtered_by_paid_status() {
        UUID memberId = UUID.randomUUID();
        MemberPrincipal principal = new MemberPrincipal(memberId, "student@test.com", "STUDENT");
        Pageable pageable = PageRequest.of(0, 10);
        PageResponse<PaymentListResponse> pageResponse = new PageResponse<>(
                List.of(new PaymentListResponse(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "소도구 필라테스 입문반",
                        5,
                        125000L,
                        PaymentStatus.PAID,
                        LocalDateTime.of(2025, 1, 1, 10, 0)
                )),
                0,
                1,
                1L,
                10,
                true
        );
        given(paymentFacade.getAllMyPayments(memberId, PaymentStatus.PAID, pageable))
                .willReturn(pageResponse);

        ResponseEntity<BaseResponse<PageResponse<PaymentListResponse>>> response =
                paymentController.getMyPayments(principal, PaymentStatus.PAID, pageable);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().content().get(0).status()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    @DisplayName("환불 가능이면 200 OK와 refundable = true 응답을 반환한다")
    void getRefundPreview_returns_200_when_refundable() {
        UUID memberId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        MemberPrincipal principal = new MemberPrincipal(memberId, "student@test.com", "STUDENT");
        RefundPreviewResponse preview = new RefundPreviewResponse(
                paymentId,
                "소도구 필라테스 입문반",
                LocalDateTime.of(2025, 1, 10, 10, 0),
                5,
                25000L,
                125000L,
                true,
                "7일 전 취소 시 전액 환불"
        );
        given(refundFacade.getRefundPreview(memberId, paymentId)).willReturn(preview);

        ResponseEntity<BaseResponse<RefundPreviewResponse>> response =
                paymentController.getRefundPreview(principal, paymentId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().message()).isEqualTo("환불 가능 여부 조회 성공");
        assertThat(response.getBody().data()).isEqualTo(preview);
    }

    @Test
    @DisplayName("환불 불가 결제면 ServiceErrorException을 전파한다")
    void getRefundPreview_propagates_exception_when_invalid_status() {
        UUID memberId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        MemberPrincipal principal = new MemberPrincipal(memberId, "student@test.com", "STUDENT");
        given(refundFacade.getRefundPreview(memberId, paymentId))
                .willThrow(new ServiceErrorException(PaymentExceptionEnum.ERR_REFUND_NOT_ALLOWED));

        assertThatThrownBy(() -> paymentController.getRefundPreview(principal, paymentId))
                .isInstanceOf(ServiceErrorException.class);
    }
}
