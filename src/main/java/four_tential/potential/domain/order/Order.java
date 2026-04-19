package four_tential.potential.domain.order;

import four_tential.potential.common.entity.BaseTimeEntity;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.OrderExceptionEnum;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "orders", uniqueConstraints = {
        @UniqueConstraint(name = "uk_orders_id", columnNames = {"id"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseTimeEntity {

    public static final int ORDER_EXPIRATION_MINUTES = 10; // 주문 만료 시간

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "course_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID courseId;

    @Column(name = "member_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID memberId;

    @Column(name = "order_count", nullable = false)
    private int orderCount;

    @Column(name = "price_snap", nullable = false)
    private BigInteger priceSnap;

    @Column(name = "total_price_snap", nullable = false)
    private BigInteger totalPriceSnap;

    @Column(name = "title_snap", nullable = false, length = 100)
    private String titleSnap;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "expire_at", nullable = false)
    private LocalDateTime expireAt;

    @Version
    private Long version;

    public static Order register(UUID memberId, UUID courseId, int orderCount, 
                               BigInteger priceSnap, String titleSnap) {
        Order order = new Order();
        order.memberId = memberId;
        order.courseId = courseId;
        order.orderCount = orderCount;
        order.priceSnap = priceSnap;
        order.totalPriceSnap = priceSnap.multiply(BigInteger.valueOf(orderCount));
        order.titleSnap = titleSnap;
        order.status = OrderStatus.PENDING;
        // 객체 생성 시점에 만료 시간을 결정하기 위해 현재 시각을 기준으로 계산
        order.expireAt = LocalDateTime.now().plusMinutes(ORDER_EXPIRATION_MINUTES);
        return order;
    }

    /**
     * 결제 완료 처리
     */
    public void completePayment() {
        if (this.status != OrderStatus.PENDING) {
            throw new ServiceErrorException(OrderExceptionEnum.ERR_NOT_PENDING_ORDER);
        }

        if (LocalDateTime.now().isAfter(this.expireAt)) {
            this.status = OrderStatus.EXPIRED; // 상태를 만료로 변경하여 다음 시도 차단
            throw new ServiceErrorException(OrderExceptionEnum.ERR_ORDER_EXPIRED);
        }

        this.status = OrderStatus.PAID;
    }

    /**
     * 주문 만료 처리
     */
    public void expire() {
        if (this.status != OrderStatus.PENDING) {
            throw new ServiceErrorException(OrderExceptionEnum.ERR_NOT_PENDING_ORDER);
        }
        this.status = OrderStatus.EXPIRED;
    }

    /**
     * 관리자에 의한 주문 상태 강제 변경
     */
    public void updateStatusByAdmin(OrderStatus nextStatus) {
        // 취소(CANCELLED) 상태로 새로 진입하는 경우만 시각 기록
        if (nextStatus == OrderStatus.CANCELLED && this.status != OrderStatus.CANCELLED) {
            this.cancelledAt = LocalDateTime.now();
        }
        // 취소 상태에서 다른 상태로 벗어나는 경우 시각 초기화
        else if (nextStatus != OrderStatus.CANCELLED && this.status == OrderStatus.CANCELLED) {
            this.cancelledAt = null;
        }

        this.status = nextStatus;
    }

    /**
     * 주문 취소 처리
     * @param courseStartDate 코스 시작 일시
     * @param now 현재 일시
     */
    public void cancel(LocalDateTime courseStartDate, LocalDateTime now) {
        if (this.status == OrderStatus.CANCELLED || this.status == OrderStatus.EXPIRED) {
            throw new ServiceErrorException(OrderExceptionEnum.ERR_CANNOT_CANCEL_ORDER);
        }

        // CONFIRMED 상태는 이미 환불 기간이 경과하여 확정된 주문이므로 취소 불가
        if (this.status == OrderStatus.CONFIRMED) {
            throw new ServiceErrorException(OrderExceptionEnum.ERR_CANNOT_CANCEL_CONFIRMED_ORDER);
        }

        // PAID 또는 PENDING 상태인 경우 7일 전 규칙 적용
        if (now.isAfter(courseStartDate.minusDays(7))) {
            throw new ServiceErrorException(OrderExceptionEnum.ERR_CANNOT_CANCEL_DATETIME);
        }

        this.status = OrderStatus.CANCELLED;
        this.cancelledAt = now;
    }
}
