package four_tential.potential.domain.payment.entity;

import four_tential.potential.domain.payment.enums.PaymentPayWay;
import four_tential.potential.domain.payment.enums.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class PaymentTest {

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
    @DisplayName("결제 생성 시 PENDING 상태로 생성된다")
    void create_status_pending() {
        Payment payment = createPayment();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("결제 생성 시 orderId 가 올바르게 저장된다")
    void create_orderId() {
        UUID orderId = UUID.randomUUID();
        Payment payment = Payment.create(
                orderId, UUID.randomUUID(), null,
                100000L, 0L, 100000L, PaymentPayWay.CARD);
        assertThat(payment.getOrderId()).isEqualTo(orderId);
    }

    @Test
    @DisplayName("결제 생성 시 memberId 가 올바르게 저장된다")
    void create_memberId() {
        UUID memberId = UUID.randomUUID();
        Payment payment = Payment.create(
                UUID.randomUUID(), memberId, null,
                100000L, 0L, 100000L, PaymentPayWay.CARD);
        assertThat(payment.getMemberId()).isEqualTo(memberId);
    }

    @Test
    @DisplayName("결제 생성 시 쿠폰 없으면 couponId 가 null 이다")
    void create_couponId_null() {
        Payment payment = createPayment();
        assertThat(payment.getCouponId()).isNull();
    }

    @Test
    @DisplayName("결제 생성 시 쿠폰 있으면 couponId 가 저장된다")
    void create_couponId_not_null() {
        UUID couponId = UUID.randomUUID();
        Payment payment = Payment.create(
                UUID.randomUUID(), UUID.randomUUID(), couponId,
                100000L, 20000L, 80000L, PaymentPayWay.CARD);
        assertThat(payment.getCouponId()).isEqualTo(couponId);
    }

    @Test
    @DisplayName("결제 생성 시 totalPrice 가 올바르게 저장된다")
    void create_totalPrice() {
        assertThat(createPayment().getTotalPrice()).isEqualTo(100000L);
    }

    @Test
    @DisplayName("결제 생성 시 discountPrice 가 올바르게 저장된다")
    void create_discountPrice() {
        Payment payment = Payment.create(
                UUID.randomUUID(), UUID.randomUUID(), null,
                100000L, 20000L, 80000L, PaymentPayWay.CARD);
        assertThat(payment.getDiscountPrice()).isEqualTo(20000L);
    }

    @Test
    @DisplayName("결제 생성 시 paidTotalPrice 가 올바르게 저장된다")
    void create_paidTotalPrice() {
        Payment payment = Payment.create(
                UUID.randomUUID(), UUID.randomUUID(), null,
                100000L, 20000L, 80000L, PaymentPayWay.CARD);
        assertThat(payment.getPaidTotalPrice()).isEqualTo(80000L);
    }

    @Test
    @DisplayName("결제 생성 시 payWay 가 올바르게 저장된다")
    void create_payWay() {
        assertThat(createPayment().getPayWay()).isEqualTo(PaymentPayWay.CARD);
    }

    @Test
    @DisplayName("결제 생성 시 paidAt 이 null 이다")
    void create_paidAt_null() {
        assertThat(createPayment().getPaidAt()).isNull();
    }

    @Test
    @DisplayName("confirmPaid 호출 시 PAID 상태로 변경된다")
    void confirmPaid_status_paid() {
        Payment payment = createPayment();
        payment.confirmPaid("portone_key_123");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    @DisplayName("confirmPaid 호출 시 pgKey 가 저장된다")
    void confirmPaid_pgKey() {
        Payment payment = createPayment();
        payment.confirmPaid("portone_key_123");
        assertThat(payment.getPgKey()).isEqualTo("portone_key_123");
    }

    @Test
    @DisplayName("confirmPaid 호출 시 paidAt 이 저장된다")
    void confirmPaid_paidAt_not_null() {
        Payment payment = createPayment();
        payment.confirmPaid("portone_key_123");
        assertThat(payment.getPaidAt()).isNotNull();
    }

    @Test
    @DisplayName("fail 호출 시 FAILED 상태로 변경된다")
    void fail_status_failed() {
        Payment payment = createPayment();
        payment.fail();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("refund 호출 시 REFUNDED 상태로 변경된다")
    void refund_status_refunded() {
        Payment payment = createPayment();
        payment.refund();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    @DisplayName("partRefund 호출 시 PART_REFUNDED 상태로 변경된다")
    void partRefund_status_part_refunded() {
        Payment payment = createPayment();
        payment.partRefund();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PART_REFUNDED);
    }
}