package four_tential.potential.domain.payment.entity;

import four_tential.potential.domain.payment.enums.RefundReason;
import four_tential.potential.domain.payment.enums.RefundStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class RefundTest {

    @Test
    @DisplayName("환불 생성 시 COMPLETED 상태로 생성된다")
    void create_status_completed() {
        Refund refund = Refund.create(UUID.randomUUID(), 50000L, RefundReason.CANCEL);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
    }

    @Test
    @DisplayName("환불 생성 시 paymentId 가 올바르게 저장된다")
    void create_paymentId() {
        UUID paymentId = UUID.randomUUID();
        Refund refund = Refund.create(paymentId, 50000L, RefundReason.CANCEL);
        assertThat(refund.getPaymentId()).isEqualTo(paymentId);
    }

    @Test
    @DisplayName("환불 생성 시 refundPrice 가 올바르게 저장된다")
    void create_refundPrice() {
        Refund refund = Refund.create(UUID.randomUUID(), 50000L, RefundReason.CANCEL);
        assertThat(refund.getRefundPrice()).isEqualTo(50000L);
    }

    @Test
    @DisplayName("환불 생성 시 reason 이 올바르게 저장된다")
    void create_reason() {
        Refund refund = Refund.create(UUID.randomUUID(), 50000L, RefundReason.INSTRUCTOR);
        assertThat(refund.getReason()).isEqualTo(RefundReason.INSTRUCTOR);
    }

    @Test
    @DisplayName("환불 생성 시 refundedAt 이 null 이 아니다")
    void create_refundedAt_not_null() {
        Refund refund = Refund.create(UUID.randomUUID(), 50000L, RefundReason.CANCEL);
        assertThat(refund.getRefundedAt()).isNotNull();
    }

    @Test
    @DisplayName("환불 생성 시 createdAt 이 null 이 아니다")
    void create_createdAt_not_null() {
        Refund refund = Refund.create(UUID.randomUUID(), 50000L, RefundReason.CANCEL);
        assertThat(refund.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("환불 생성 시 updatedAt 이 null 이 아니다")
    void create_updatedAt_not_null() {
        Refund refund = Refund.create(UUID.randomUUID(), 50000L, RefundReason.CANCEL);
        assertThat(refund.getUpdatedAt()).isNotNull();
    }
}