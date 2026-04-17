package four_tential.potential.domain.coupon.entity;

import four_tential.potential.common.exception.ServiceErrorException;
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

    private MemberCoupon memberCoupon(MemberCouponStatus status) {
        MemberCoupon memberCoupon = new MemberCoupon();
        ReflectionTestUtils.setField(memberCoupon, "status", status);
        return memberCoupon;
    }
}
