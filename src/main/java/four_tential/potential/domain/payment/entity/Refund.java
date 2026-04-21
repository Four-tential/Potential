package four_tential.potential.domain.payment.entity;

import four_tential.potential.common.entity.BaseTimeEntity;
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
public class Refund extends BaseTimeEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "refund_price", nullable = false)
    private Long refundPrice;

    @Column(name = "cancel_count", nullable = false)
    private int cancelCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RefundReason reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RefundStatus status;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    /**
     * PortOne 환불이 성공했을 때 남기는 완료 이력이다.
     * cancelCount는 이번에 취소된 수강권 수량이다.
     */
    public static Refund completed(Payment payment, Long refundPrice, int cancelCount, RefundReason reason) {
        Refund refund = new Refund();
        refund.payment = payment;
        refund.refundPrice = refundPrice;
        refund.cancelCount = cancelCount;
        refund.reason = reason;
        refund.status = RefundStatus.COMPLETED;
        refund.refundedAt = LocalDateTime.now();
        return refund;
    }

    /**
     * PortOne 환불이 실패했을 때도 기록은 남긴다.
     * 운영자가 나중에 어떤 환불이 실패했는지 확인하기 위함이다.
     */
    public static Refund failed(Payment payment, Long refundPrice, int cancelCount, RefundReason reason) {
        Refund refund = new Refund();
        refund.payment = payment;
        refund.refundPrice = refundPrice;
        refund.cancelCount = cancelCount;
        refund.reason = reason;
        refund.status = RefundStatus.FAILED;
        return refund;
    }
}
