package four_tential.potential.domain.coupon.entity;

import four_tential.potential.common.entity.BaseTimeEntity;
import four_tential.potential.domain.coupon.enums.DiscountType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "coupon_policies")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponPolicy extends BaseTimeEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(nullable = false, length = 300)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 30)
    private DiscountType discountType;

    @Column(name = "discount_price", nullable = false)
    private Long discountPrice;

    @Column(name = "min_pay_price", nullable = false)
    private Long minPayPrice;

    @Column(name = "max_discount_price")
    private Long maxDiscountPrice;

    @Column(name = "total_quantity")
    private Long totalQuantity;

    @Column(name = "issued_quantity")
    private Long issuedQuantity;

    @Column(name = "start_dt", nullable = false)
    private LocalDateTime startDt;

    @Column(name = "end_dt", nullable = false)
    private LocalDateTime endDt;

    // Todo: 생성 팩토리 — 쿠폰 담당자 파트에서 작성 예정


    // Todo: 발급 관련 — 쿠폰 담당자 파트에서 작성 예정


    // Todo: 쿠폰이 현재 사용 가능한 기간인지 확인


    // Todo: 실제 할인 금액 계산


}
