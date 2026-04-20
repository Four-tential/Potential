package four_tential.potential.domain.payment.entity;

import four_tential.potential.common.entity.BaseTimeEntity;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.PaymentExceptionEnum;
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
@Table(name = "payments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payments_order_id", columnNames = {"order_id"})
})
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

    @Column(name = "pg_key", unique = true, length = 300)
    private String pgKey;

    @Column(name = "total_price", nullable = false)
    private Long totalPrice;

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

    public static Payment createPending(
            UUID orderId,
            UUID memberId,
            String pgKey,
            Long totalPrice,
            Long paidTotalPrice,
            PaymentPayWay payWay) {
        Payment payment = new Payment();
        payment.orderId = orderId;
        payment.memberId = memberId;
        payment.pgKey = pgKey;
        payment.totalPrice = totalPrice;
        payment.paidTotalPrice = paidTotalPrice;
        payment.payWay = payWay;
        payment.status = PaymentStatus.PENDING;
        return payment;
    }

    public boolean isPending() {
        return this.status == PaymentStatus.PENDING;
    }

    public boolean isPaid() {
        return this.status == PaymentStatus.PAID;
    }

    // 결제를 PAID 상태로 확정
    public void confirmPaid() {
        if (transitTo(PaymentStatus.PAID)) {
            this.paidAt = LocalDateTime.now();
        }
    }

    public void fail() {
        transitTo(PaymentStatus.FAILED);
    }

    public void refund() {
        transitTo(PaymentStatus.REFUNDED);
    }

    public void partRefund() {
        transitTo(PaymentStatus.PART_REFUNDED);
    }

    private boolean transitTo(PaymentStatus target) {
        if (this.status == target) {
            return false;
        }
        if (!this.status.canTransitTo(target)) {
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_INVALID_PAYMENT_STATUS_TRANSITION);
        }

        this.status = target;
        return true;
    }
}
