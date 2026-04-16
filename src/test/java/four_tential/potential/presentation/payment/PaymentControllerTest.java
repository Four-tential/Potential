package four_tential.potential.presentation.payment;

import four_tential.potential.application.payment.PaymentFacade;
import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.domain.payment.enums.PaymentPayWay;
import four_tential.potential.domain.payment.enums.PaymentStatus;
import four_tential.potential.infra.security.principal.MemberPrincipal;
import four_tential.potential.presentation.payment.dto.PaymentCreateRequest;
import four_tential.potential.presentation.payment.dto.PaymentCreateResponse;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @InjectMocks
    private PaymentController paymentController;

    @Mock
    private PaymentFacade paymentFacade;

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
}
