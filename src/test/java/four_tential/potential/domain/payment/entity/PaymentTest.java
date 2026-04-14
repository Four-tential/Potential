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
        Payment payment = Payment.create(
                UUID.randomUUID(), UUID.randomUUID(), null,
                100000L, 0L, 100000L, PaymentPayWay.CARD);
        assertThat(payment.getTotalPrice()).isEqualTo(100000L);
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
        Payment payment = createPayment();
        assertThat(payment.getPayWay()).isEqualTo(PaymentPayWay.CARD);
    }

    @Test
    @DisplayName("결제 생성 시 paidAt 이 null 이다")
    void create_paidAt_null() {
        Payment payment = createPayment();
        assertThat(payment.getPaidAt()).isNull();
    }
}