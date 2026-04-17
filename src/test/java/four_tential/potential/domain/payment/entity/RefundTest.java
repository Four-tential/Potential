package four_tential.potential.domain.payment.entity;

import four_tential.potential.domain.payment.enums.PaymentPayWay;
import four_tential.potential.domain.payment.enums.RefundReason;
import four_tential.potential.domain.payment.enums.RefundStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

class RefundTest {

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
    @DisplayName("환불 생성 시 COMPLETED 상태로 생성된다")
    void create_status_completed() {
        Payment payment = createPayment();
        Refund refund = Refund.create(payment, 50000L, RefundReason.CANCEL);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
    }

    @Test
    @DisplayName("환불 생성 시 payment 가 올바르게 저장된다")
    void create_payment() {
        Payment payment = createPayment();
        Refund refund = Refund.create(payment, 50000L, RefundReason.CANCEL);
        assertThat(refund.getPayment()).isEqualTo(payment);
    }

    @Test
    @DisplayName("환불 생성 시 refundPrice 가 올바르게 저장된다")
    void create_refundPrice() {
        Payment payment = createPayment();
        Refund refund = Refund.create(payment, 50000L, RefundReason.CANCEL);
        assertThat(refund.getRefundPrice()).isEqualTo(50000L);
    }

    @Test
    @DisplayName("환불 생성 시 reason 이 CANCEL 로 저장된다")
    void create_reason_cancel() {
        Payment payment = createPayment();
        Refund refund = Refund.create(payment, 50000L, RefundReason.CANCEL);
        assertThat(refund.getReason()).isEqualTo(RefundReason.CANCEL);
    }

    @Test
    @DisplayName("환불 생성 시 reason 이 INSTRUCTOR 로 저장된다")
    void create_reason_instructor() {
        Payment payment = createPayment();
        Refund refund = Refund.create(payment, 100000L, RefundReason.INSTRUCTOR);
        assertThat(refund.getReason()).isEqualTo(RefundReason.INSTRUCTOR);
    }

    @Test
    @DisplayName("환불 생성 시 refundedAt 이 null 이 아니다")
    void create_refundedAt_not_null() {
        Payment payment = createPayment();
        Refund refund = Refund.create(payment, 50000L, RefundReason.CANCEL);
        assertThat(refund.getRefundedAt()).isNotNull();
    }

    @Test
    @DisplayName("환불 실패 생성 시 FAILED 상태로 생성된다")
    void fail_status_failed() {
        Payment payment = createPayment();
        Refund refund = Refund.fail(payment, 50000L, RefundReason.CANCEL);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.FAILED);
    }

    @Test
    @DisplayName("환불 실패 생성 시 payment 가 올바르게 저장된다")
    void fail_payment() {
        Payment payment = createPayment();
        Refund refund = Refund.fail(payment, 50000L, RefundReason.CANCEL);
        assertThat(refund.getPayment()).isEqualTo(payment);
    }

    @Test
    @DisplayName("환불 실패 생성 시 refundedAt 이 null 이다")
    void fail_refundedAt_null() {
        Payment payment = createPayment();
        Refund refund = Refund.fail(payment, 50000L, RefundReason.CANCEL);
        assertThat(refund.getRefundedAt()).isNull();
    }

    @Test
    @DisplayName("환불 실패 생성 시 refundPrice 가 올바르게 저장된다")
    void fail_refundPrice() {
        Payment payment = createPayment();
        Refund refund = Refund.fail(payment, 50000L, RefundReason.CANCEL);
        assertThat(refund.getRefundPrice()).isEqualTo(50000L);
    }
}
