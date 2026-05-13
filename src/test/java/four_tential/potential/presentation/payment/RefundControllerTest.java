package four_tential.potential.presentation.payment;

import four_tential.potential.application.payment.RefundFacade;
import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.common.dto.PageResponse;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.PaymentExceptionEnum;
import four_tential.potential.domain.payment.enums.RefundReason;
import four_tential.potential.domain.payment.enums.RefundStatus;
import four_tential.potential.infra.security.principal.MemberPrincipal;
import four_tential.potential.presentation.payment.dto.RefundDetailResponse;
import four_tential.potential.presentation.payment.dto.RefundListResponse;
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
class RefundControllerTest {

    @InjectMocks
    private RefundController refundController;

    @Mock
    private RefundFacade refundFacade;

    @Test
    @DisplayName("본인 환불 단건 조회면 200 OK 와 RefundDetailResponse 를 반환한다")
    void getMyRefund_returns_200_with_detail() {
        UUID memberId = UUID.randomUUID();
        UUID refundId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        MemberPrincipal principal = new MemberPrincipal(memberId, "student@test.com", "STUDENT");
        RefundDetailResponse detail = new RefundDetailResponse(
                refundId,
                paymentId,
                "소도구 필라테스 입문반",
                2,
                50000L,
                RefundReason.CANCEL,
                RefundStatus.COMPLETED,
                LocalDateTime.of(2026, 4, 21, 10, 0)
        );
        given(refundFacade.getMyRefund(memberId, refundId)).willReturn(detail);

        ResponseEntity<BaseResponse<RefundDetailResponse>> response =
                refundController.getMyRefund(principal, refundId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().status()).isEqualTo(HttpStatus.OK.name());
        assertThat(response.getBody().message()).isEqualTo("환불 조회 성공");
        assertThat(response.getBody().data()).isEqualTo(detail);
        verify(refundFacade).getMyRefund(memberId, refundId);
    }

    @Test
    @DisplayName("환불 단건 조회 대상이 없으면 ServiceErrorException 이 전파된다")
    void getMyRefund_propagates_exception_when_not_found() {
        UUID memberId = UUID.randomUUID();
        UUID refundId = UUID.randomUUID();
        MemberPrincipal principal = new MemberPrincipal(memberId, "student@test.com", "STUDENT");
        given(refundFacade.getMyRefund(memberId, refundId))
                .willThrow(new ServiceErrorException(PaymentExceptionEnum.ERR_NOT_FOUND_REFUND));

        assertThatThrownBy(() -> refundController.getMyRefund(principal, refundId))
                .isInstanceOf(ServiceErrorException.class);
    }

    @Test
    @DisplayName("환불 목록 조회면 200 OK 와 PageResponse 를 반환한다")
    void getMyRefunds_returns_200_with_page_response() {
        UUID memberId = UUID.randomUUID();
        MemberPrincipal principal = new MemberPrincipal(memberId, "student@test.com", "STUDENT");
        Pageable pageable = PageRequest.of(0, 10);
        PageResponse<RefundListResponse> pageResponse = new PageResponse<>(
                List.of(new RefundListResponse(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "소도구 필라테스 입문반",
                        1,
                        25000L,
                        RefundReason.CANCEL,
                        RefundStatus.COMPLETED,
                        LocalDateTime.of(2026, 4, 21, 11, 0)
                )),
                0, 1, 1L, 10, true
        );
        given(refundFacade.getAllMyRefunds(memberId, RefundStatus.COMPLETED, pageable))
                .willReturn(pageResponse);

        ResponseEntity<BaseResponse<PageResponse<RefundListResponse>>> response =
                refundController.getMyRefunds(principal, RefundStatus.COMPLETED, pageable);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().message()).isEqualTo("환불 목록 조회 성공");
        assertThat(response.getBody().data()).isEqualTo(pageResponse);
        verify(refundFacade).getAllMyRefunds(memberId, RefundStatus.COMPLETED, pageable);
    }
}
