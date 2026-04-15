package four_tential.potential.domain.payment.entity;

import four_tential.potential.common.entity.BaseTimeEntity;
import four_tential.potential.domain.payment.enums.PaymentPayWay;
import four_tential.potential.domain.payment.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "payments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseTimeEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "order_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID orderId;

    @Column(name = "member_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID memberId;

    @Column(name = "coupon_id", columnDefinition = "BINARY(16)")
    private UUID couponId;

    @Column(name = "pg_key", unique = true, length = 300)
    private String pgKey;

    @Column(name = "total_price", nullable = false)
    private Long totalPrice;

    @Column(name = "discount_price", nullable = false)
    private Long discountPrice;

    @Column(name = "paid_total_price", nullable = false)
    private Long paidTotalPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "pay_way", nullable = false, length = 30)
    private PaymentPayWay payWay;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus status;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    public static Payment create(
            UUID orderId,
            UUID memberId,
            UUID couponId,
            Long totalPrice,
            Long discountPrice,
            Long paidTotalPrice,
            PaymentPayWay payWay) {
        Payment payment = new Payment();
        payment.orderId = orderId;
        payment.memberId = memberId;
        payment.couponId = couponId;
        payment.totalPrice = totalPrice;
        payment.discountPrice = discountPrice;
        payment.paidTotalPrice = paidTotalPrice;
        payment.payWay = payWay;
        payment.status = PaymentStatus.PENDING;
        return payment;
    }

    /**
     * 결제 확정 처리
     * 웹훅 Transaction.Paid 수신 시 호출
     */
    public void confirmPaid(String pgKey) {
        this.pgKey = pgKey;
        this.status = PaymentStatus.PAID;
        this.paidAt = LocalDateTime.now();
    }

    /**
     * 결제 실패 처리
     * 웹훅 Transaction.Failed 수신 시 호출
     */
    public void fail() {
        this.status = PaymentStatus.FAILED;
    }

    public void refund() {
        this.status = PaymentStatus.REFUNDED;
    }

    public void partRefund() {
        this.status = PaymentStatus.PART_REFUNDED;
    }
}
