package four_tential.potential.domain.coupon.entity;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.coupon.enums.DiscountType;
import four_tential.potential.domain.coupon.enums.MemberCouponStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberCouponTest {

    @Test
    @DisplayName("회원 쿠폰은 저장된 필드 값을 조회할 수 있다")
    void memberCoupon_getters_return_values() {
        MemberCoupon memberCoupon = new MemberCoupon();
        CouponPolicy couponPolicy = new CouponPolicy();
        UUID id = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        LocalDateTime issuedAt = LocalDateTime.of(2026, 4, 17, 10, 0);

        ReflectionTestUtils.setField(memberCoupon, "id", id);
        ReflectionTestUtils.setField(memberCoupon, "couponPolicy", couponPolicy);
        ReflectionTestUtils.setField(memberCoupon, "memberId", memberId);
        ReflectionTestUtils.setField(memberCoupon, "status", MemberCouponStatus.ISSUED);
        ReflectionTestUtils.setField(memberCoupon, "issuedAt", issuedAt);

        assertThat(memberCoupon.getId()).isEqualTo(id);
        assertThat(memberCoupon.getCouponPolicy()).isEqualTo(couponPolicy);
        assertThat(memberCoupon.getMemberId()).isEqualTo(memberId);
        assertThat(memberCoupon.getStatus()).isEqualTo(MemberCouponStatus.ISSUED);
        assertThat(memberCoupon.getIssuedAt()).isEqualTo(issuedAt);
    }

    @Test
    @DisplayName("ISSUED 상태의 회원 쿠폰은 USED 상태로 변경할 수 있다")
    void issued_can_transit_to_used() {
        MemberCoupon memberCoupon = memberCoupon(MemberCouponStatus.ISSUED);

        ReflectionTestUtils.invokeMethod(memberCoupon, "transitTo", MemberCouponStatus.USED);

        assertThat(memberCoupon.getStatus()).isEqualTo(MemberCouponStatus.USED);
    }

    @Test
    @DisplayName("USED 상태의 회원 쿠폰은 ISSUED 상태로 복구할 수 있다")
    void used_can_transit_to_issued() {
        MemberCoupon memberCoupon = memberCoupon(MemberCouponStatus.USED);

        ReflectionTestUtils.invokeMethod(memberCoupon, "transitTo", MemberCouponStatus.ISSUED);

        assertThat(memberCoupon.getStatus()).isEqualTo(MemberCouponStatus.ISSUED);
    }

    @Test
    @DisplayName("같은 상태로 변경하면 상태 값은 그대로 유지된다")
    void same_status_keeps_current_status() {
        MemberCoupon memberCoupon = memberCoupon(MemberCouponStatus.ISSUED);

        ReflectionTestUtils.invokeMethod(memberCoupon, "transitTo", MemberCouponStatus.ISSUED);

        assertThat(memberCoupon.getStatus()).isEqualTo(MemberCouponStatus.ISSUED);
    }

    @Test
    @DisplayName("허용되지 않은 상태 변경이면 예외가 발생한다")
    void invalid_transition_throws_exception() {
        MemberCoupon memberCoupon = memberCoupon(MemberCouponStatus.EXPIRED);

        assertThatThrownBy(() ->
                ReflectionTestUtils.invokeMethod(memberCoupon, "transitTo", MemberCouponStatus.ISSUED))
                .isInstanceOf(ServiceErrorException.class);
        assertThat(memberCoupon.getStatus()).isEqualTo(MemberCouponStatus.EXPIRED);
    }

    @Test
    @DisplayName("ISSUED 상태이고 쿠폰 정책 기간이 유효하면 회원 쿠폰은 사용할 수 있다")
    void isUsable_returns_true_when_issued_and_policy_available() {
        MemberCoupon memberCoupon = memberCoupon(MemberCouponStatus.ISSUED, availableCouponPolicy());

        assertThat(memberCoupon.isUsable()).isTrue();
    }

    @Test
    @DisplayName("회원 쿠폰 상태가 ISSUED가 아니면 사용할 수 없다")
    void isUsable_returns_false_when_status_is_not_issued() {
        MemberCoupon memberCoupon = memberCoupon(MemberCouponStatus.USED, availableCouponPolicy());

        assertThat(memberCoupon.isUsable()).isFalse();
    }

    @Test
    @DisplayName("쿠폰 정책 기간이 유효하지 않으면 사용할 수 없다")
    void isUsable_returns_false_when_policy_is_not_available() {
        MemberCoupon memberCoupon = memberCoupon(MemberCouponStatus.ISSUED, expiredCouponPolicy());

        assertThat(memberCoupon.isUsable()).isFalse();
    }

    @Test
    @DisplayName("사용 가능한 회원 쿠폰은 결제 완료 시 USED 상태가 된다")
    void use_changes_status_to_used_when_coupon_is_usable() {
        MemberCoupon memberCoupon = memberCoupon(MemberCouponStatus.ISSUED, availableCouponPolicy());

        memberCoupon.use();

        assertThat(memberCoupon.getStatus()).isEqualTo(MemberCouponStatus.USED);
    }

    @Test
    @DisplayName("사용할 수 없는 회원 쿠폰은 use 호출 시 예외가 발생한다")
    void use_throws_when_coupon_is_not_usable() {
        MemberCoupon memberCoupon = memberCoupon(MemberCouponStatus.USED, availableCouponPolicy());

        assertThatThrownBy(memberCoupon::use)
                .isInstanceOf(ServiceErrorException.class);
        assertThat(memberCoupon.getStatus()).isEqualTo(MemberCouponStatus.USED);
    }

    @Test
    @DisplayName("USED 상태의 회원 쿠폰은 결제 취소 시 ISSUED 상태로 복구된다")
    void restore_changes_status_to_issued() {
        MemberCoupon memberCoupon = memberCoupon(MemberCouponStatus.USED, availableCouponPolicy());

        memberCoupon.restore();

        assertThat(memberCoupon.getStatus()).isEqualTo(MemberCouponStatus.ISSUED);
    }

    @Test
    @DisplayName("복구할 수 없는 상태의 회원 쿠폰은 restore 호출 시 예외가 발생한다")
    void restore_throws_when_transition_is_invalid() {
        MemberCoupon memberCoupon = memberCoupon(MemberCouponStatus.EXPIRED, availableCouponPolicy());

        assertThatThrownBy(memberCoupon::restore)
                .isInstanceOf(ServiceErrorException.class);
        assertThat(memberCoupon.getStatus()).isEqualTo(MemberCouponStatus.EXPIRED);
    }

    @Test
    @DisplayName("사용 가능한 회원 쿠폰은 쿠폰 정책 기준으로 할인 금액을 계산한다")
    void calculateDiscountAmount_returns_policy_discount_amount() {
        MemberCoupon memberCoupon = memberCoupon(MemberCouponStatus.ISSUED, availableCouponPolicy());

        long discountAmount = memberCoupon.calculateDiscountAmount(100000L);

        assertThat(discountAmount).isEqualTo(10000L);
    }

    @Test
    @DisplayName("사용할 수 없는 회원 쿠폰은 할인 금액 계산 시 예외가 발생한다")
    void calculateDiscountAmount_throws_when_coupon_is_not_usable() {
        MemberCoupon memberCoupon = memberCoupon(MemberCouponStatus.USED, availableCouponPolicy());

        assertThatThrownBy(() -> memberCoupon.calculateDiscountAmount(100000L))
                .isInstanceOf(ServiceErrorException.class);
    }

    private MemberCoupon memberCoupon(MemberCouponStatus status) {
        MemberCoupon memberCoupon = new MemberCoupon();
        ReflectionTestUtils.setField(memberCoupon, "status", status);
        return memberCoupon;
    }

    private MemberCoupon memberCoupon(MemberCouponStatus status, CouponPolicy couponPolicy) {
        MemberCoupon memberCoupon = memberCoupon(status);
        ReflectionTestUtils.setField(memberCoupon, "couponPolicy", couponPolicy);
        return memberCoupon;
    }

    private CouponPolicy availableCouponPolicy() {
        return couponPolicy(LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));
    }

    private CouponPolicy expiredCouponPolicy() {
        return couponPolicy(LocalDateTime.now().minusDays(2), LocalDateTime.now().minusDays(1));
    }

    private CouponPolicy couponPolicy(LocalDateTime startDt, LocalDateTime endDt) {
        CouponPolicy couponPolicy = new CouponPolicy();
        ReflectionTestUtils.setField(couponPolicy, "discountType", DiscountType.FIX);
        ReflectionTestUtils.setField(couponPolicy, "discountPrice", 10000L);
        ReflectionTestUtils.setField(couponPolicy, "minPayPrice", 50000L);
        ReflectionTestUtils.setField(couponPolicy, "startDt", startDt);
        ReflectionTestUtils.setField(couponPolicy, "endDt", endDt);
        return couponPolicy;
    }
}
