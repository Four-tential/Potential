package four_tential.potential.domain.coupon.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiscountTypeTest {

    @Test
    @DisplayName("쿠폰 할인 구분은 정액 할인과 정률 할인을 가진다")
    void discountType_has_fix_and_rate() {
        assertThat(DiscountType.values()).containsExactly(DiscountType.FIX, DiscountType.RATE);
    }
}
