package four_tential.potential.domain.payment.entity;

import four_tential.potential.domain.payment.enums.RefundReason;
import four_tential.potential.domain.payment.enums.RefundStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "refunds")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Refund {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "refund_price", nullable = false)
    private Long refundPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RefundReason reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RefundStatus status;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static Refund create(Payment payment, Long refundPrice, RefundReason reason) {
        Refund refund = new Refund();
        refund.payment = payment;
        refund.refundPrice = refundPrice;
        refund.reason = reason;
        refund.status = RefundStatus.COMPLETED;
        refund.refundedAt = LocalDateTime.now();
        refund.createdAt = LocalDateTime.now();
        refund.updatedAt = LocalDateTime.now();
        return refund;
    }

    public static Refund fail(Payment payment, Long refundPrice, RefundReason reason) {
        Refund refund = new Refund();
        refund.payment = payment;
        refund.refundPrice = refundPrice;
        refund.reason = reason;
        refund.status = RefundStatus.FAILED;
        refund.createdAt = LocalDateTime.now();
        refund.updatedAt = LocalDateTime.now();
        return refund;
    }
}
