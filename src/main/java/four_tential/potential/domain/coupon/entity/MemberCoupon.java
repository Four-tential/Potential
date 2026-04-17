package four_tential.potential.domain.coupon.entity;

import four_tential.potential.common.entity.BaseTimeEntity;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.CouponExceptionEnum;
import four_tential.potential.domain.coupon.enums.MemberCouponStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "member_coupons")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberCoupon extends BaseTimeEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_policy_id", nullable = false)
    private CouponPolicy couponPolicy;

    @Column(name = "member_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MemberCouponStatus status;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private LocalDateTime issuedAt;


    // Todo: 생성 팩토리 - 쿠폰 담당자 파트에서 작성 예정


    // Todo: 쿠폰이 현재 사용 가능한 상태인지 확인


    // Todo: 결제 완료 시 쿠폰 사용 처리


    // Todo: 결제 취소 시 쿠폰 복구


    // Todo: 쿠폰 적용 후 실제 결제 금액 계산


    private void transitTo(MemberCouponStatus target) {
        if (this.status == target) {
            return;
        }
        if (!this.status.canTransitTo(target)) {
            throw new ServiceErrorException(CouponExceptionEnum.ERR_COUPON_INVALID_STATUS_TRANSITION);
        }
        this.status = target;
    }
}
