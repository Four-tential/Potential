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
                "portone_key_123",
                100000L,
                100000L,
                PaymentPayWay.CARD
        );
    }

    @Test
    @DisplayName("환불 완료 이력은 COMPLETED 상태로 생성된다")
    void completed_status_completed() {
        Payment payment = createPayment();
        Refund refund = Refund.completed(payment, 50000L, 1, RefundReason.CANCEL);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
    }

    @Test
    @DisplayName("환불 완료 이력은 payment 를 함께 저장한다")
    void completed_payment() {
        Payment payment = createPayment();
        Refund refund = Refund.completed(payment, 50000L, 1, RefundReason.CANCEL);
        assertThat(refund.getPayment()).isEqualTo(payment);
    }

    @Test
    @DisplayName("환불 완료 이력은 refundPrice 를 저장한다")
    void completed_refundPrice() {
        Payment payment = createPayment();
        Refund refund = Refund.completed(payment, 50000L, 1, RefundReason.CANCEL);
        assertThat(refund.getRefundPrice()).isEqualTo(50000L);
    }

    @Test
    @DisplayName("환불 완료 이력은 취소 수량을 저장한다")
    void completed_cancelCount() {
        Payment payment = createPayment();
        Refund refund = Refund.completed(payment, 50000L, 2, RefundReason.CANCEL);
        assertThat(refund.getCancelCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("환불 완료 이력은 reason 을 저장한다")
    void completed_reason() {
        Payment payment = createPayment();
        Refund refund = Refund.completed(payment, 100000L, 1, RefundReason.INSTRUCTOR);
        assertThat(refund.getReason()).isEqualTo(RefundReason.INSTRUCTOR);
    }

    @Test
    @DisplayName("환불 완료 이력은 refundedAt 을 기록한다")
    void completed_refundedAt_not_null() {
        Payment payment = createPayment();
        Refund refund = Refund.completed(payment, 50000L, 1, RefundReason.CANCEL);
        assertThat(refund.getRefundedAt()).isNotNull();
    }

    @Test
    @DisplayName("환불 실패 이력은 FAILED 상태로 생성된다")
    void failed_status_failed() {
        Payment payment = createPayment();
        Refund refund = Refund.failed(payment, 50000L, 1, RefundReason.CANCEL);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.FAILED);
    }

    @Test
    @DisplayName("환불 실패 이력은 payment 를 함께 저장한다")
    void failed_payment() {
        Payment payment = createPayment();
        Refund refund = Refund.failed(payment, 50000L, 1, RefundReason.CANCEL);
        assertThat(refund.getPayment()).isEqualTo(payment);
    }

    @Test
    @DisplayName("환불 실패 이력은 refundedAt 을 비워둔다")
    void failed_refundedAt_null() {
        Payment payment = createPayment();
        Refund refund = Refund.failed(payment, 50000L, 1, RefundReason.CANCEL);
        assertThat(refund.getRefundedAt()).isNull();
    }

    @Test
    @DisplayName("환불 실패 이력은 refundPrice 와 취소 수량을 저장한다")
    void failed_refundPrice_and_cancelCount() {
        Payment payment = createPayment();
        Refund refund = Refund.failed(payment, 50000L, 1, RefundReason.CANCEL);
        assertThat(refund.getRefundPrice()).isEqualTo(50000L);
        assertThat(refund.getCancelCount()).isEqualTo(1);
    }
}
