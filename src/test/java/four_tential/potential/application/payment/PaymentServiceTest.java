package four_tential.potential.application.payment;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.payment.entity.Payment;
import four_tential.potential.domain.payment.enums.PaymentPayWay;
import four_tential.potential.domain.payment.enums.PaymentStatus;
import four_tential.potential.domain.payment.port.PaymentGatewayResponse;
import four_tential.potential.domain.payment.repository.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
                null,
                "portone_key_123",
                100000L,
                0L,
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
                null,
                100000L,
                0L,
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
                null,
                100000L,
                0L,
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
                null,
                100000L,
                0L,
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
                .hasMessage("결제 식별자가 일치하지 않습니다");
    }

    @Test
    @DisplayName("validateGatewayPayment 는 요청 결제 수단이 카드가 아니면 예외가 발생한다")
    void validateGatewayPayment_throws_when_request_payWay_not_card() {
        PaymentCreateCommand preparation = new PaymentCreateCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                100000L,
                0L,
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
                null,
                100000L,
                0L,
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
                null,
                100000L,
                0L,
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
                null,
                100000L,
                0L,
                100000L
        );
        Payment payment = Payment.createPending(
                orderId,
                memberId,
                null,
                "pg-key-1",
                100000L,
                0L,
                100000L,
                PaymentPayWay.CARD
        );
        given(paymentRepository.save(any(Payment.class))).willReturn(payment);

        Payment result = paymentService.createPendingPayment(preparation, "pg-key-1", PaymentPayWay.CARD);

        assertThat(result).isEqualTo(payment);
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
}
