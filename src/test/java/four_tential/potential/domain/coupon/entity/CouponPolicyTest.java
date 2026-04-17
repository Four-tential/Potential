package four_tential.potential.domain.coupon.entity;

import four_tential.potential.domain.coupon.enums.DiscountType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CouponPolicyTest {

    @Test
    @DisplayName("쿠폰 정책은 저장된 필드 값을 조회할 수 있다")
    void couponPolicy_getters_return_values() {
        CouponPolicy couponPolicy = new CouponPolicy();
        UUID id = UUID.randomUUID();
        LocalDateTime startDt = LocalDateTime.of(2026, 4, 1, 0, 0);
        LocalDateTime endDt = LocalDateTime.of(2026, 4, 30, 23, 59);

        ReflectionTestUtils.setField(couponPolicy, "id", id);
        ReflectionTestUtils.setField(couponPolicy, "name", "신규 회원 쿠폰");
        ReflectionTestUtils.setField(couponPolicy, "discountType", DiscountType.FIX);
        ReflectionTestUtils.setField(couponPolicy, "discountPrice", 10000L);
        ReflectionTestUtils.setField(couponPolicy, "minPayPrice", 50000L);
        ReflectionTestUtils.setField(couponPolicy, "maxDiscountPrice", 20000L);
        ReflectionTestUtils.setField(couponPolicy, "totalQuantity", 100L);
        ReflectionTestUtils.setField(couponPolicy, "issuedQuantity", 10L);
        ReflectionTestUtils.setField(couponPolicy, "startDt", startDt);
        ReflectionTestUtils.setField(couponPolicy, "endDt", endDt);

        assertThat(couponPolicy.getId()).isEqualTo(id);
        assertThat(couponPolicy.getName()).isEqualTo("신규 회원 쿠폰");
        assertThat(couponPolicy.getDiscountType()).isEqualTo(DiscountType.FIX);
        assertThat(couponPolicy.getDiscountPrice()).isEqualTo(10000L);
        assertThat(couponPolicy.getMinPayPrice()).isEqualTo(50000L);
        assertThat(couponPolicy.getMaxDiscountPrice()).isEqualTo(20000L);
        assertThat(couponPolicy.getTotalQuantity()).isEqualTo(100L);
        assertThat(couponPolicy.getIssuedQuantity()).isEqualTo(10L);
        assertThat(couponPolicy.getStartDt()).isEqualTo(startDt);
        assertThat(couponPolicy.getEndDt()).isEqualTo(endDt);
    }
}
