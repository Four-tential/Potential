package four_tential.potential.domain.coupon.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemberCouponStatusTest {

    @Test
    @DisplayName("ISSUED 상태는 USED 또는 EXPIRED 상태로 변경할 수 있다")
    void issued_can_transit_to_used_or_expired() {
        assertThat(MemberCouponStatus.ISSUED.canTransitTo(MemberCouponStatus.USED)).isTrue();
        assertThat(MemberCouponStatus.ISSUED.canTransitTo(MemberCouponStatus.EXPIRED)).isTrue();
        assertThat(MemberCouponStatus.ISSUED.canTransitTo(MemberCouponStatus.ISSUED)).isFalse();
    }

    @Test
    @DisplayName("USED 상태는 ISSUED 상태로만 복구할 수 있다")
    void used_can_transit_to_issued_only() {
        assertThat(MemberCouponStatus.USED.canTransitTo(MemberCouponStatus.ISSUED)).isTrue();
        assertThat(MemberCouponStatus.USED.canTransitTo(MemberCouponStatus.USED)).isFalse();
        assertThat(MemberCouponStatus.USED.canTransitTo(MemberCouponStatus.EXPIRED)).isFalse();
    }

    @Test
    @DisplayName("EXPIRED 상태는 다른 상태로 변경할 수 없다")
    void expired_cannot_transit_to_any_status() {
        assertThat(MemberCouponStatus.EXPIRED.canTransitTo(MemberCouponStatus.ISSUED)).isFalse();
        assertThat(MemberCouponStatus.EXPIRED.canTransitTo(MemberCouponStatus.USED)).isFalse();
        assertThat(MemberCouponStatus.EXPIRED.canTransitTo(MemberCouponStatus.EXPIRED)).isFalse();
    }
}
