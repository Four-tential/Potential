package four_tential.potential.application.payment;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.payment.entity.Payment;
import four_tential.potential.domain.payment.enums.PaymentPayWay;
import four_tential.potential.domain.payment.enums.PaymentStatus;
import four_tential.potential.domain.payment.repository.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
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
        return Payment.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
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
        given(paymentRepository.findByPgKey(any(String.class)))
                .willReturn(Optional.of(payment));

        Payment result = paymentService.getByPgKey("portone_key_123");

        assertThat(result).isEqualTo(payment);
    }

    @Test
    @DisplayName("getByPgKey 호출 시 Payment 없으면 예외 발생")
    void getByPgKey_throws_when_not_found() {
        given(paymentRepository.findByPgKey(any(String.class)))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getByPgKey("portone_key_123"))
                .isInstanceOf(ServiceErrorException.class);
    }

    @Test
    @DisplayName("save 호출 시 Payment 를 저장하고 반환한다")
    void save_returns_saved_payment() {
        Payment payment = createPayment();
        given(paymentRepository.save(any(Payment.class)))
                .willReturn(payment);

        Payment result = paymentService.save(payment);

        assertThat(result).isEqualTo(payment);
        verify(paymentRepository).save(payment);
    }

    @Test
    @DisplayName("confirmPaid 호출 시 Payment 상태가 PAID 로 변경된다")
    void confirmPaid_changes_status_to_paid() {
        Payment payment = createPayment();
        given(paymentRepository.findByPgKey(any(String.class)))
                .willReturn(Optional.of(payment));

        paymentService.confirmPaid("portone_key_123");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getPgKey()).isEqualTo("portone_key_123");
        assertThat(payment.getPaidAt()).isNotNull();
    }

    @Test
    @DisplayName("fail 호출 시 Payment 상태가 FAILED 로 변경된다")
    void fail_changes_status_to_failed() {
        Payment payment = createPayment();
        given(paymentRepository.findByPgKey(any(String.class)))
                .willReturn(Optional.of(payment));

        paymentService.fail("portone_key_123");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }
}