package four_tential.potential.domain.order;

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
public class Order {

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

    @Column(name = "order_at", nullable = false)
    private LocalDateTime orderAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "expire_at", nullable = false)
    private LocalDateTime expireAt;

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
        order.orderAt = LocalDateTime.now();
        order.expireAt = order.orderAt.plusMinutes(ORDER_EXPIRATION_MINUTES);
        return order;
    }
}
