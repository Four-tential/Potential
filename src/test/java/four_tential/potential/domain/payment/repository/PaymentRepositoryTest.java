package four_tential.potential.domain.payment.repository;

import four_tential.potential.domain.payment.entity.Payment;
import four_tential.potential.domain.payment.enums.PaymentPayWay;
import four_tential.potential.infra.redis.RedisTestContainer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class PaymentRepositoryTest extends RedisTestContainer {

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    @DisplayName("findByPgKey 호출 시 pgKey 가 일치하는 결제를 조회한다")
    void findByPgKey_returns_payment() {
        Payment payment = createPayment(UUID.randomUUID(), "pg-key-1");
        paymentRepository.saveAndFlush(payment);

        Optional<Payment> result = paymentRepository.findByPgKey("pg-key-1");

        assertThat(result).isPresent();
        assertThat(result.get().getPgKey()).isEqualTo("pg-key-1");
    }

    @Test
    @DisplayName("findByPgKey 호출 시 pgKey 가 없으면 빈 Optional 을 반환한다")
    void findByPgKey_returns_empty_when_not_found() {
        Optional<Payment> result = paymentRepository.findByPgKey("missing-pg-key");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByOrderId 호출 시 orderId 가 일치하는 결제를 조회한다")
    void findByOrderId_returns_payment() {
        UUID orderId = UUID.randomUUID();
        Payment payment = createPayment(orderId, "pg-key-by-order");
        paymentRepository.saveAndFlush(payment);

        Optional<Payment> result = paymentRepository.findByOrderId(orderId);

        assertThat(result).isPresent();
        assertThat(result.get().getOrderId()).isEqualTo(orderId);
    }

    @Test
    @DisplayName("findByPgKeyForUpdate 호출 시 pgKey 가 일치하는 결제를 조회한다")
    void findByPgKeyForUpdate_returns_payment() {
        Payment payment = createPayment(UUID.randomUUID(), "pg-key-lock");
        paymentRepository.saveAndFlush(payment);

        Optional<Payment> result = paymentRepository.findByPgKeyForUpdate("pg-key-lock");

        assertThat(result).isPresent();
        assertThat(result.get().getPgKey()).isEqualTo("pg-key-lock");
    }

    @Test
    @DisplayName("existsByOrderId 호출 시 같은 주문의 결제가 있으면 true 를 반환한다")
    void existsByOrderId_returns_true_when_exists() {
        UUID orderId = UUID.randomUUID();
        Payment payment = createPayment(orderId, "pg-key-order");
        paymentRepository.saveAndFlush(payment);

        boolean result = paymentRepository.existsByOrderId(orderId);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("existsByOrderId 호출 시 같은 주문의 결제가 없으면 false 를 반환한다")
    void existsByOrderId_returns_false_when_not_exists() {
        boolean result = paymentRepository.existsByOrderId(UUID.randomUUID());

        assertThat(result).isFalse();
    }

    private Payment createPayment(UUID orderId, String pgKey) {
        return Payment.createPending(
                orderId,
                UUID.randomUUID(),
                null,
                pgKey,
                100000L,
                0L,
                100000L,
                PaymentPayWay.CARD
        );
    }
}
