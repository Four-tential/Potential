package four_tential.potential.infra.portone;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.payment.entity.Payment;
import four_tential.potential.domain.payment.enums.PaymentPayWay;
import four_tential.potential.domain.payment.port.PaymentGatewayRequest;
import four_tential.potential.domain.payment.port.PaymentGatewayResponse;
import four_tential.potential.domain.payment.repository.PaymentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class LocalPaymentGatewayStubTest {

    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final LocalPaymentGatewayStubProperties properties = new LocalPaymentGatewayStubProperties();

    @AfterEach
    void clearInterruptedFlag() {
        Thread.interrupted();
    }

    @ParameterizedTest
    @CsvSource({
            "PENDING, CARD, READY, card",
            "PAID, CARD, PAID, card",
            "FAILED, CARD, FAILED, card",
            "PART_REFUNDED, EASY_PAY, PARTIAL_CANCELLED, easyPay",
            "REFUNDED, EASY_PAY, CANCELLED, easyPay"
    })
    @DisplayName("getPayment은 payment 상태와 결제 수단을 게이트웨이 응답 형식으로 변환한다")
    void getPayment_maps_status_and_pay_method(
            String paymentStatus,
            String payWay,
            String expectedGatewayStatus,
            String expectedGatewayPayMethod
    ) {
        properties.setGetPaymentDelay(Duration.ZERO);
        LocalPaymentGatewayStub stub = new LocalPaymentGatewayStub(paymentRepository, properties);
        Payment payment = createPayment(paymentStatus, payWay);

        given(paymentRepository.findByPgKey(payment.getPgKey())).willReturn(Optional.of(payment));

        PaymentGatewayResponse response = stub.getPayment(payment.getPgKey());

        assertThat(response.pgKey()).isEqualTo(payment.getPgKey());
        assertThat(response.status()).isEqualTo(expectedGatewayStatus);
        assertThat(response.totalAmount()).isEqualTo(payment.getPaidTotalPrice());
        assertThat(response.payMethod()).isEqualTo(expectedGatewayPayMethod);
    }

    @Test
    @DisplayName("getPayment은 pgKey에 해당하는 payment가 없으면 예외를 던진다")
    void getPayment_throws_when_payment_not_found() {
        properties.setGetPaymentDelay(Duration.ZERO);
        LocalPaymentGatewayStub stub = new LocalPaymentGatewayStub(paymentRepository, properties);

        given(paymentRepository.findByPgKey("missing-pg-key")).willReturn(Optional.empty());

        assertThatThrownBy(() -> stub.getPayment("missing-pg-key"))
                .isInstanceOf(ServiceErrorException.class);
    }

    @Test
    @DisplayName("cancelPayment은 stub 지연 후에도 예외 없이 종료된다")
    void cancelPayment_completes_without_exception() {
        properties.setCancelPaymentDelay(Duration.ZERO);
        LocalPaymentGatewayStub stub = new LocalPaymentGatewayStub(paymentRepository, properties);

        PaymentGatewayRequest request = PaymentGatewayRequest.of("pg-key-1", 1000L, "CANCEL");

        assertThatCode(() -> stub.cancelPayment(request))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("인터럽트가 걸린 상태에서 호출되어도 인터럽트 상태를 복구한다")
    void cancelPayment_preserves_interrupted_state() {
        properties.setCancelPaymentDelay(Duration.ofMillis(1));
        LocalPaymentGatewayStub stub = new LocalPaymentGatewayStub(paymentRepository, properties);

        Thread.currentThread().interrupt();

        stub.cancelPayment(PaymentGatewayRequest.of("pg-key-1", 1000L, "CANCEL"));

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    private Payment createPayment(String paymentStatus, String payWay) {
        Payment payment = Payment.createPending(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "stub-pg-key-" + paymentStatus + "-" + payWay,
                50000L,
                50000L,
                PaymentPayWay.valueOf(payWay)
        );

        switch (paymentStatus) {
            case "PENDING" -> {
            }
            case "PAID" -> payment.confirmPaid();
            case "FAILED" -> payment.fail();
            case "PART_REFUNDED" -> {
                payment.confirmPaid();
                payment.partRefund();
            }
            case "REFUNDED" -> {
                payment.confirmPaid();
                payment.refund();
            }
            default -> throw new IllegalArgumentException("Unsupported payment status for test: " + paymentStatus);
        }

        return payment;
    }
}
