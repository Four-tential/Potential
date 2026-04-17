package four_tential.potential.domain.payment.entity;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.payment.enums.PaymentPayWay;
import four_tential.potential.domain.payment.enums.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

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
    @DisplayName("결제 생성 시 PENDING 상태로 생성된다")
    void create_status_pending() {
        Payment payment = createPayment();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("결제 생성 시 orderId 가 저장된다")
    void create_orderId() {
        UUID orderId = UUID.randomUUID();
        Payment payment = Payment.createPending(
                orderId,
                UUID.randomUUID(),
                null,
                "portone_key_123",
                100000L,
                0L,
                100000L,
                PaymentPayWay.CARD
        );

        assertThat(payment.getOrderId()).isEqualTo(orderId);
    }

    @Test
    @DisplayName("결제 생성 시 memberId 가 저장된다")
    void create_memberId() {
        UUID memberId = UUID.randomUUID();
        Payment payment = Payment.createPending(
                UUID.randomUUID(),
                memberId,
                null,
                "portone_key_123",
                100000L,
                0L,
                100000L,
                PaymentPayWay.CARD
        );

        assertThat(payment.getMemberId()).isEqualTo(memberId);
    }

    @Test
    @DisplayName("결제 생성 시 쿠폰이 없으면 memberCouponId 는 null 이다")
    void create_memberCouponId_null() {
        Payment payment = createPayment();
        assertThat(payment.getMemberCouponId()).isNull();
    }

    @Test
    @DisplayName("결제 생성 시 쿠폰이 있으면 memberCouponId 가 저장된다")
    void create_memberCouponId_not_null() {
        UUID memberCouponId = UUID.randomUUID();
        Payment payment = Payment.createPending(
                UUID.randomUUID(),
                UUID.randomUUID(),
                memberCouponId,
                "portone_key_123",
                100000L,
                20000L,
                80000L,
                PaymentPayWay.CARD
        );

        assertThat(payment.getMemberCouponId()).isEqualTo(memberCouponId);
    }

    @Test
    @DisplayName("결제 생성 시 totalPrice 가 저장된다")
    void create_totalPrice() {
        assertThat(createPayment().getTotalPrice()).isEqualTo(100000L);
    }

    @Test
    @DisplayName("결제 생성 시 discountPrice 가 저장된다")
    void create_discountPrice() {
        Payment payment = Payment.createPending(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                "portone_key_123",
                100000L,
                20000L,
                80000L,
                PaymentPayWay.CARD
        );

        assertThat(payment.getDiscountPrice()).isEqualTo(20000L);
    }

    @Test
    @DisplayName("결제 생성 시 paidTotalPrice 가 저장된다")
    void create_paidTotalPrice() {
        Payment payment = Payment.createPending(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                "portone_key_123",
                100000L,
                20000L,
                80000L,
                PaymentPayWay.CARD
        );

        assertThat(payment.getPaidTotalPrice()).isEqualTo(80000L);
    }

    @Test
    @DisplayName("결제 생성 시 payWay 가 저장된다")
    void create_payWay() {
        assertThat(createPayment().getPayWay()).isEqualTo(PaymentPayWay.CARD);
    }

    @Test
    @DisplayName("결제 생성 시 paidAt 은 null 이다")
    void create_paidAt_null() {
        assertThat(createPayment().getPaidAt()).isNull();
    }

    @Test
    @DisplayName("confirmPaid 호출 시 PAID 상태로 변경된다")
    void confirmPaid_status_paid() {
        Payment payment = createPayment();
        payment.confirmPaid();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    @DisplayName("confirmPaid 호출 시 기존 pgKey 가 유지된다")
    void confirmPaid_pgKey() {
        Payment payment = createPayment();
        payment.confirmPaid();

        assertThat(payment.getPgKey()).isEqualTo("portone_key_123");
    }

    @Test
    @DisplayName("confirmPaid 호출 시 paidAt 이 저장된다")
    void confirmPaid_paidAt_not_null() {
        Payment payment = createPayment();
        payment.confirmPaid();

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
        payment.confirmPaid();
        payment.refund();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    @DisplayName("partRefund 호출 시 PART_REFUNDED 상태로 변경된다")
    void partRefund_status_part_refunded() {
        Payment payment = createPayment();
        payment.confirmPaid();
        payment.partRefund();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PART_REFUNDED);
    }

    @Test
    @DisplayName("PAID 상태에서는 FAILED 상태로 변경할 수 없다")
    void paid_cannot_change_to_failed() {
        Payment payment = createPayment();
        payment.confirmPaid();

        assertThatThrownBy(payment::fail)
                .isInstanceOf(ServiceErrorException.class);
    }

    @Test
    @DisplayName("PENDING 상태에서는 REFUNDED 상태로 변경할 수 없다")
    void pending_cannot_change_to_refunded() {
        Payment payment = createPayment();

        assertThatThrownBy(payment::refund)
                .isInstanceOf(ServiceErrorException.class);
    }

    @Test
    @DisplayName("PART_REFUNDED 상태에서는 FAILED 상태로 변경할 수 없다")
    void partRefunded_cannot_change_to_failed() {
        Payment payment = createPayment();
        payment.confirmPaid();
        payment.partRefund();

        assertThatThrownBy(payment::fail)
                .isInstanceOf(ServiceErrorException.class);
    }
}
