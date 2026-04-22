package four_tential.potential.application.payment;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.PaymentExceptionEnum;
import four_tential.potential.domain.payment.entity.Payment;
import four_tential.potential.domain.payment.enums.PaymentPayWay;
import four_tential.potential.domain.payment.enums.PaymentStatus;
import four_tential.potential.domain.payment.port.PaymentGatewayResponse;
import four_tential.potential.domain.payment.repository.PaymentRepository;
import four_tential.potential.presentation.payment.dto.PaymentDetailResponse;
import four_tential.potential.presentation.payment.dto.PaymentListResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @InjectMocks
    private PaymentService paymentService;

    @Mock
    private PaymentRepository paymentRepository;

    private Payment createPayment() {
        return Payment.createPending(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "portone_key_123",
                100000L,
                100000L,
                PaymentPayWay.CARD
        );
    }

    @Test
    @DisplayName("getByPgKey 호출 시 Payment 를 반환한다")
    void getByPgKey_returns_payment() {
        Payment payment = createPayment();
        given(paymentRepository.findByPgKey(any(String.class))).willReturn(Optional.of(payment));

        Payment result = paymentService.getByPgKey("portone_key_123");

        assertThat(result).isEqualTo(payment);
    }

    @Test
    @DisplayName("getByPgKey 호출 시 Payment 가 없으면 예외가 발생한다")
    void getByPgKey_throws_when_not_found() {
        given(paymentRepository.findByPgKey(any(String.class))).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getByPgKey("portone_key_123"))
                .isInstanceOf(ServiceErrorException.class);
    }

    @Test
    @DisplayName("findByPgKey 호출 시 Optional Payment 를 반환한다")
    void findByPgKey_returns_optional_payment() {
        Payment payment = createPayment();
        given(paymentRepository.findByPgKey("portone_key_123")).willReturn(Optional.of(payment));

        Optional<Payment> result = paymentService.findByPgKey("portone_key_123");

        assertThat(result).contains(payment);
    }

    @Test
    @DisplayName("findByOrderId 호출 시 Optional Payment 를 반환한다")
    void findByOrderId_returns_optional_payment() {
        UUID orderId = UUID.randomUUID();
        Payment payment = createPayment();
        given(paymentRepository.findByOrderId(orderId)).willReturn(Optional.of(payment));

        Optional<Payment> result = paymentService.findByOrderId(orderId);

        assertThat(result).contains(payment);
    }

    @Test
    @DisplayName("getByPgKeyForUpdate 호출 시 Payment 를 반환한다")
    void getByPgKeyForUpdate_returns_payment() {
        Payment payment = createPayment();
        given(paymentRepository.findByPgKeyForUpdate("portone_key_123")).willReturn(Optional.of(payment));

        Payment result = paymentService.getByPgKeyForUpdate("portone_key_123");

        assertThat(result).isEqualTo(payment);
    }

    @Test
    @DisplayName("getByPgKeyForUpdate 호출 시 Payment 가 없으면 예외가 발생한다")
    void getByPgKeyForUpdate_throws_when_not_found() {
        given(paymentRepository.findByPgKeyForUpdate("missing")).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getByPgKeyForUpdate("missing"))
                .isInstanceOf(ServiceErrorException.class);
    }

    @Test
    @DisplayName("findByPgKeyForUpdate 호출 시 Optional Payment 를 반환한다")
    void findByPgKeyForUpdate_returns_optional_payment() {
        Payment payment = createPayment();
        given(paymentRepository.findByPgKeyForUpdate("portone_key_123")).willReturn(Optional.of(payment));

        Optional<Payment> result = paymentService.findByPgKeyForUpdate("portone_key_123");

        assertThat(result).contains(payment);
    }

    @Test
    @DisplayName("validateNoPayment 는 같은 주문의 결제가 이미 있으면 예외가 발생한다")
    void validateNoPayment_throws_when_payment_exists() {
        UUID orderId = UUID.randomUUID();
        given(paymentRepository.existsByOrderId(orderId)).willReturn(true);

        assertThatThrownBy(() -> paymentService.validateNoPayment(orderId))
                .isInstanceOf(ServiceErrorException.class);
    }

    @Test
    @DisplayName("validateNoPayment 는 같은 주문의 결제가 없으면 통과한다")
    void validateNoPayment_passes_when_payment_not_exists() {
        UUID orderId = UUID.randomUUID();
        given(paymentRepository.existsByOrderId(orderId)).willReturn(false);

        paymentService.validateNoPayment(orderId);
    }

    @Test
    @DisplayName("validateGatewayPayment 는 서버 계산 금액과 PortOne 결제 금액이 같으면 통과한다")
    void validateGatewayPayment_success() {
        PaymentCreateCommand preparation = new PaymentCreateCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                100000L,
                100000L
        );
        PaymentGatewayResponse gatewayResponse = new PaymentGatewayResponse(
                "pg-key-1",
                "PAID",
                100000L,
                "card"
        );

        paymentService.validateGatewayPayment(preparation, "pg-key-1", PaymentPayWay.CARD, gatewayResponse);
    }

    @Test
    @DisplayName("validateGatewayPayment 는 PortOne 결제 금액이 다르면 예외가 발생한다")
    void validateGatewayPayment_throws_when_amount_mismatch() {
        PaymentCreateCommand preparation = new PaymentCreateCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                100000L,
                100000L
        );
        PaymentGatewayResponse gatewayResponse = new PaymentGatewayResponse(
                "pg-key-1",
                "PAID",
                90000L,
                "card"
        );

        assertThatThrownBy(() ->
                paymentService.validateGatewayPayment(preparation, "pg-key-1", PaymentPayWay.CARD, gatewayResponse))
                .isInstanceOf(ServiceErrorException.class);
    }

    @Test
    @DisplayName("validateGatewayPayment 는 요청 pgKey 와 PortOne pgKey 가 다르면 식별자 불일치 예외가 발생한다")
    void validateGatewayPayment_throws_when_pgKey_mismatch() {
        PaymentCreateCommand preparation = new PaymentCreateCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                100000L,
                100000L
        );
        PaymentGatewayResponse gatewayResponse = new PaymentGatewayResponse(
                "pg-key-from-portone",
                "PAID",
                100000L,
                "card"
        );

        assertThatThrownBy(() ->
                paymentService.validateGatewayPayment(preparation, "pg-key-from-request", PaymentPayWay.CARD, gatewayResponse))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(PaymentExceptionEnum.ERR_PAYMENT_KEY_MISMATCH.getMessage());
    }

    @Test
    @DisplayName("validateGatewayPayment 는 요청 결제 수단이 카드가 아니면 예외가 발생한다")
    void validateGatewayPayment_throws_when_request_payWay_not_card() {
        PaymentCreateCommand preparation = new PaymentCreateCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                100000L,
                100000L
        );
        PaymentGatewayResponse gatewayResponse = new PaymentGatewayResponse(
                "pg-key-1",
                "PAID",
                100000L,
                "card"
        );

        assertThatThrownBy(() ->
                paymentService.validateGatewayPayment(preparation, "pg-key-1", PaymentPayWay.EASY_PAY, gatewayResponse))
                .isInstanceOf(ServiceErrorException.class);
    }

    @Test
    @DisplayName("validateGatewayPayment 는 PortOne 결제가 완료 상태가 아니면 예외가 발생한다")
    void validateGatewayPayment_throws_when_gateway_status_not_paid() {
        PaymentCreateCommand preparation = new PaymentCreateCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                100000L,
                100000L
        );
        PaymentGatewayResponse gatewayResponse = new PaymentGatewayResponse(
                "pg-key-1",
                "READY",
                100000L,
                "card"
        );

        assertThatThrownBy(() ->
                paymentService.validateGatewayPayment(preparation, "pg-key-1", PaymentPayWay.CARD, gatewayResponse))
                .isInstanceOf(ServiceErrorException.class);
    }

    @Test
    @DisplayName("validateGatewayPayment 는 PortOne 결제 수단이 카드가 아니면 예외가 발생한다")
    void validateGatewayPayment_throws_when_gateway_payMethod_not_card() {
        PaymentCreateCommand preparation = new PaymentCreateCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                100000L,
                100000L
        );
        PaymentGatewayResponse gatewayResponse = new PaymentGatewayResponse(
                "pg-key-1",
                "PAID",
                100000L,
                "easyPay"
        );

        assertThatThrownBy(() ->
                paymentService.validateGatewayPayment(preparation, "pg-key-1", PaymentPayWay.CARD, gatewayResponse))
                .isInstanceOf(ServiceErrorException.class);
    }

    @Test
    @DisplayName("save 호출 시 Payment 를 저장한다")
    void save_returns_saved_payment() {
        Payment payment = createPayment();
        given(paymentRepository.save(payment)).willReturn(payment);

        Payment result = paymentService.save(payment);

        assertThat(result).isEqualTo(payment);
        verify(paymentRepository).save(payment);
    }

    @Test
    @DisplayName("createPendingPayment 는 PENDING 결제를 저장한다")
    void createPendingPayment_returns_saved_payment() {
        UUID orderId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        PaymentCreateCommand preparation = new PaymentCreateCommand(
                orderId,
                memberId,
                100000L,
                100000L
        );
        Payment payment = Payment.createPending(
                orderId,
                memberId,
                "pg-key-1",
                100000L,
                100000L,
                PaymentPayWay.CARD
        );
        given(paymentRepository.save(any(Payment.class))).willReturn(payment);

        Payment result = paymentService.createPendingPayment(preparation, "pg-key-1", PaymentPayWay.CARD);

        assertThat(result).isEqualTo(payment);
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    @DisplayName("createFailedPayment 는 FAILED 결제를 저장한다")
    void createFailedPayment_returns_saved_failed_payment() {
        UUID orderId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        PaymentCreateCommand preparation = new PaymentCreateCommand(
                orderId,
                memberId,
                100000L,
                100000L
        );
        given(paymentRepository.save(any(Payment.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        Payment result = paymentService.createFailedPayment(preparation, "pg-key-failed", PaymentPayWay.CARD);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.getOrderId()).isEqualTo(orderId);
        assertThat(result.getPgKey()).isEqualTo("pg-key-failed");
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    @DisplayName("confirmPaid 호출 시 Payment 상태가 PAID 로 변경된다")
    void confirmPaid_changes_status_to_paid() {
        Payment payment = createPayment();

        paymentService.confirmPaid(payment);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getPaidAt()).isNotNull();
    }

    @Test
    @DisplayName("fail 호출 시 Payment 상태가 FAILED 로 변경된다")
    void fail_changes_status_to_failed() {
        Payment payment = createPayment();

        paymentService.fail(payment);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("refund 호출 시 Payment 상태가 REFUNDED 로 변경된다")
    void refund_changes_status_to_refunded() {
        Payment payment = createPayment();
        payment.confirmPaid();

        paymentService.refund(payment);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    @DisplayName("partRefund 호출 시 Payment 상태가 PART_REFUNDED 로 변경된다")
    void partRefund_changes_status_to_part_refunded() {
        Payment payment = createPayment();
        payment.confirmPaid();

        paymentService.partRefund(payment);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PART_REFUNDED);
    }

    @Test
    @DisplayName("본인 결제가 존재하면 PaymentDetailResponse 를 반환한다")
    void getDetailByIdAndMemberId_returns_response_when_found() {
        UUID paymentId = UUID.randomUUID();
        UUID memberId  = UUID.randomUUID();
        UUID orderId   = UUID.randomUUID();
        PaymentDetailResponse expected = new PaymentDetailResponse(
                paymentId, orderId, "소도구 필라테스 입문반", 5,
                125000L, 125000L,
                PaymentPayWay.CARD, PaymentStatus.PAID,
                LocalDateTime.of(2025, 1, 1, 10, 0)
        );
        given(paymentRepository.findDetailByIdAndMemberId(paymentId, memberId))
                .willReturn(Optional.of(expected));

        PaymentDetailResponse result = paymentService.getMyPayment(paymentId, memberId);

        assertThat(result).isEqualTo(expected);
        verify(paymentRepository).findDetailByIdAndMemberId(paymentId, memberId);
    }

    @Test
    @DisplayName("결제가 없거나 타인의 결제면 NOT_FOUND 예외가 발생한다")
    void getDetailByIdAndMemberId_throws_when_not_found() {
        UUID paymentId = UUID.randomUUID();
        UUID memberId  = UUID.randomUUID();
        given(paymentRepository.findDetailByIdAndMemberId(paymentId, memberId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getMyPayment(paymentId, memberId))
                .isInstanceOf(ServiceErrorException.class);
    }

    @Test
    @DisplayName("status 가 null 이면 전체 결제 목록을 반환한다")
    void getListByMemberIdAndStatus_returns_all_when_status_null() {
        UUID memberId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        List<PaymentListResponse> items = List.of(
                createListResponse(UUID.randomUUID(), UUID.randomUUID(), PaymentStatus.PAID),
                createListResponse(UUID.randomUUID(), UUID.randomUUID(), PaymentStatus.PENDING)
        );
        Page<PaymentListResponse> page = new PageImpl<>(items, pageable, 2);
        given(paymentRepository.findListByMemberIdAndStatus(memberId, null, pageable))
                .willReturn(page);

        Page<PaymentListResponse> result =
                paymentService.getAllMyPayments(memberId, null, pageable);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(2);
        verify(paymentRepository).findListByMemberIdAndStatus(memberId, null, pageable);
    }

    @Test
    @DisplayName("status 가 PAID 이면 PAID 결제 목록만 반환한다")
    void getListByMemberIdAndStatus_returns_filtered_when_status_given() {
        UUID memberId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        List<PaymentListResponse> items = List.of(
                createListResponse(UUID.randomUUID(), UUID.randomUUID(), PaymentStatus.PAID)
        );
        Page<PaymentListResponse> page = new PageImpl<>(items, pageable, 1);
        given(paymentRepository.findListByMemberIdAndStatus(memberId, PaymentStatus.PAID, pageable))
                .willReturn(page);

        Page<PaymentListResponse> result =
                paymentService.getAllMyPayments(memberId, PaymentStatus.PAID, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).status()).isEqualTo(PaymentStatus.PAID);
        verify(paymentRepository).findListByMemberIdAndStatus(memberId, PaymentStatus.PAID, pageable);
    }

    @Test
    @DisplayName("결제 내역이 없으면 빈 페이지를 반환한다")
    void getListByMemberIdAndStatus_returns_empty_page() {
        UUID memberId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        Page<PaymentListResponse> emptyPage = new PageImpl<>(List.of(), pageable, 0);
        given(paymentRepository.findListByMemberIdAndStatus(memberId, null, pageable))
                .willReturn(emptyPage);

        Page<PaymentListResponse> result =
                paymentService.getAllMyPayments(memberId, null, pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    private PaymentListResponse createListResponse(
            UUID paymentId, UUID orderId, PaymentStatus status) {
        return new PaymentListResponse(
                paymentId, orderId, "소도구 필라테스 입문반", 5,
                125000L, status, LocalDateTime.of(2025, 1, 1, 10, 0)
        );
    }
}
