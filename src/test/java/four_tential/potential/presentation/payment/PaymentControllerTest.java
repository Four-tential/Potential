package four_tential.potential.presentation.payment;

import four_tential.potential.application.payment.PaymentFacade;
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
    @DisplayName("본인 결제이면 200 OK 와 PaymentDetailResponse 를 반환한다")
    void getMyPayment_returns_200_with_detail() {
        UUID memberId  = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId   = UUID.randomUUID();
        MemberPrincipal principal = new MemberPrincipal(memberId, "student@test.com", "STUDENT");
        PaymentDetailResponse detail = new PaymentDetailResponse(
                paymentId, orderId, "소도구 필라테스 입문반", 5,
                125000L, 0L, 125000L,
                PaymentPayWay.CARD, PaymentStatus.PAID,
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

        PaymentDetailResponse data = response.getBody().data();
        assertThat(data.paymentId()).isEqualTo(paymentId);
        assertThat(data.orderId()).isEqualTo(orderId);
        assertThat(data.courseTitle()).isEqualTo("소도구 필라테스 입문반");
        assertThat(data.orderCount()).isEqualTo(5);
        assertThat(data.paidTotalPrice()).isEqualTo(125000L);
        assertThat(data.status()).isEqualTo(PaymentStatus.PAID);
        verify(paymentFacade).getMyPayment(memberId, paymentId);
    }

    @Test
    @DisplayName("결제가 없거나 타인의 결제면 ServiceErrorException 이 전파된다")
    void getMyPayment_propagates_exception_when_not_found() {
        UUID memberId  = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        MemberPrincipal principal = new MemberPrincipal(memberId, "student@test.com", "STUDENT");
        given(paymentFacade.getMyPayment(memberId, paymentId))
                .willThrow(new ServiceErrorException(PaymentExceptionEnum.ERR_NOT_FOUND_PAYMENT));

        assertThatThrownBy(() -> paymentController.getMyPayment(principal, paymentId))
                .isInstanceOf(ServiceErrorException.class);
    }

    @Test
    @DisplayName("status 가 null 이면 전체 결제 목록을 200 OK 로 반환한다")
    void getMyPayments_returns_200_with_all_when_status_null() {
        UUID memberId = UUID.randomUUID();
        MemberPrincipal principal = new MemberPrincipal(memberId, "student@test.com", "STUDENT");
        Pageable pageable = PageRequest.of(0, 10);
        PageResponse<PaymentListResponse> pageResponse = new PageResponse<>(
                List.of(new PaymentListResponse(
                        UUID.randomUUID(), UUID.randomUUID(),
                        "소도구 필라테스 입문반", 5, 125000L,
                        PaymentStatus.PAID, LocalDateTime.of(2025, 1, 1, 10, 0)
                )),
                0, 1, 1L, 10, true
        );
        given(paymentFacade.getAllMyPayments(memberId, null, pageable)).willReturn(pageResponse);

        ResponseEntity<BaseResponse<PageResponse<PaymentListResponse>>> response =
                paymentController.getMyPayments(principal, null, pageable);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().message()).isEqualTo("결제 목록 조회 성공");

        PageResponse<PaymentListResponse> data = response.getBody().data();
        assertThat(data.content()).hasSize(1);
        assertThat(data.content().get(0).courseTitle()).isEqualTo("소도구 필라테스 입문반");
        assertThat(data.content().get(0).orderCount()).isEqualTo(5);
        assertThat(data.totalElements()).isEqualTo(1L);
        assertThat(data.currentPage()).isZero();
        assertThat(data.isLast()).isTrue();
        verify(paymentFacade).getAllMyPayments(memberId, null, pageable);
    }

    @Test
    @DisplayName("status 가 PAID 이면 PAID 결제 목록만 반환한다")
    void getMyPayments_returns_filtered_by_paid_status() {
        UUID memberId = UUID.randomUUID();
        MemberPrincipal principal = new MemberPrincipal(memberId, "student@test.com", "STUDENT");
        Pageable pageable = PageRequest.of(0, 10);
        PageResponse<PaymentListResponse> pageResponse = new PageResponse<>(
                List.of(new PaymentListResponse(
                        UUID.randomUUID(), UUID.randomUUID(),
                        "소도구 필라테스 입문반", 5, 125000L,
                        PaymentStatus.PAID, LocalDateTime.of(2025, 1, 1, 10, 0)
                )),
                0, 1, 1L, 10, true
        );
        given(paymentFacade.getAllMyPayments(memberId, PaymentStatus.PAID, pageable))
                .willReturn(pageResponse);

        ResponseEntity<BaseResponse<PageResponse<PaymentListResponse>>> response =
                paymentController.getMyPayments(principal, PaymentStatus.PAID, pageable);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().content().get(0).status())
                .isEqualTo(PaymentStatus.PAID);
        verify(paymentFacade).getAllMyPayments(memberId, PaymentStatus.PAID, pageable);
    }

    @Test
    @DisplayName("결제 내역이 없으면 빈 목록을 200 OK 로 반환한다")
    void getMyPayments_returns_empty_list() {
        UUID memberId = UUID.randomUUID();
        MemberPrincipal principal = new MemberPrincipal(memberId, "student@test.com", "STUDENT");
        Pageable pageable = PageRequest.of(0, 10);
        PageResponse<PaymentListResponse> emptyPage =
                new PageResponse<>(List.of(), 0, 0, 0L, 10, true);
        given(paymentFacade.getAllMyPayments(memberId, null, pageable)).willReturn(emptyPage);

        ResponseEntity<BaseResponse<PageResponse<PaymentListResponse>>> response =
                paymentController.getMyPayments(principal, null, pageable);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().content()).isEmpty();
        assertThat(response.getBody().data().totalElements()).isZero();
    }

    @Test
    @DisplayName("페이지네이션 메타 정보가 올바르게 반환된다")
    void getMyPayments_returns_correct_pagination_meta() {
        UUID memberId = UUID.randomUUID();
        MemberPrincipal principal = new MemberPrincipal(memberId, "student@test.com", "STUDENT");
        Pageable pageable = PageRequest.of(0, 10);
        PageResponse<PaymentListResponse> pageResponse = new PageResponse<>(
                List.of(new PaymentListResponse(
                        UUID.randomUUID(), UUID.randomUUID(),
                        "강좌A", 1, 50000L,
                        PaymentStatus.PAID, LocalDateTime.now()
                )),
                0, 3, 25L, 10, false
        );
        given(paymentFacade.getAllMyPayments(memberId, null, pageable)).willReturn(pageResponse);

        ResponseEntity<BaseResponse<PageResponse<PaymentListResponse>>> response =
                paymentController.getMyPayments(principal, null, pageable);

        PageResponse<PaymentListResponse> data = response.getBody().data();
        assertThat(data.currentPage()).isZero();
        assertThat(data.totalPages()).isEqualTo(3);
        assertThat(data.totalElements()).isEqualTo(25L);
        assertThat(data.size()).isEqualTo(10);
        assertThat(data.isLast()).isFalse();
    }
}
