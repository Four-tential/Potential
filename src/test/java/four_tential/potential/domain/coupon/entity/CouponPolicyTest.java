package four_tential.potential.domain.coupon.entity;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.coupon.enums.DiscountType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    @DisplayName("현재 시간이 사용 기간 안에 있으면 쿠폰 정책은 사용 가능하다")
    void isAvailable_returns_true_when_now_is_between_start_and_end() {
        CouponPolicy couponPolicy = couponPolicy(
                DiscountType.FIX,
                10000L,
                50000L,
                null,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(1)
        );

        assertThat(couponPolicy.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("현재 시간이 시작일 전이면 쿠폰 정책은 사용할 수 없다")
    void isAvailable_returns_false_when_now_is_before_start() {
        CouponPolicy couponPolicy = couponPolicy(
                DiscountType.FIX,
                10000L,
                50000L,
                null,
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(2)
        );

        assertThat(couponPolicy.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("현재 시간이 종료일 후이면 쿠폰 정책은 사용할 수 없다")
    void isAvailable_returns_false_when_now_is_after_end() {
        CouponPolicy couponPolicy = couponPolicy(
                DiscountType.FIX,
                10000L,
                50000L,
                null,
                LocalDateTime.now().minusDays(2),
                LocalDateTime.now().minusDays(1)
        );

        assertThat(couponPolicy.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("정액 할인 쿠폰은 discountPrice 만큼 할인한다")
    void calculateDiscountAmount_fix_discount() {
        CouponPolicy couponPolicy = couponPolicy(DiscountType.FIX, 10000L, 50000L, null);

        long discountAmount = couponPolicy.calculateDiscountAmount(100000L);

        assertThat(discountAmount).isEqualTo(10000L);
    }

    @Test
    @DisplayName("정액 할인 금액이 주문 금액보다 크면 주문 금액까지만 할인한다")
    void calculateDiscountAmount_fix_discount_cannot_exceed_original_price() {
        CouponPolicy couponPolicy = couponPolicy(DiscountType.FIX, 10000L, 1000L, null);

        long discountAmount = couponPolicy.calculateDiscountAmount(5000L);

        assertThat(discountAmount).isEqualTo(5000L);
    }

    @Test
    @DisplayName("정률 할인 쿠폰은 주문 금액에 할인율을 곱해 할인한다")
    void calculateDiscountAmount_rate_discount_without_max_price() {
        CouponPolicy couponPolicy = couponPolicy(DiscountType.RATE, 10L, 50000L, null);

        long discountAmount = couponPolicy.calculateDiscountAmount(100000L);

        assertThat(discountAmount).isEqualTo(10000L);
    }

    @Test
    @DisplayName("정률 할인 쿠폰은 최대 할인 금액을 넘을 수 없다")
    void calculateDiscountAmount_rate_discount_with_max_price() {
        CouponPolicy couponPolicy = couponPolicy(DiscountType.RATE, 50L, 50000L, 20000L);

        long discountAmount = couponPolicy.calculateDiscountAmount(100000L);

        assertThat(discountAmount).isEqualTo(20000L);
    }

    @Test
    @DisplayName("주문 금액이 최소 결제 금액보다 작으면 할인 금액 계산 시 예외가 발생한다")
    void calculateDiscountAmount_throws_when_original_price_is_less_than_min_price() {
        CouponPolicy couponPolicy = couponPolicy(DiscountType.FIX, 10000L, 50000L, null);

        assertThatThrownBy(() -> couponPolicy.calculateDiscountAmount(49999L))
                .isInstanceOf(ServiceErrorException.class);
    }

    private CouponPolicy couponPolicy(
            DiscountType discountType,
            Long discountPrice,
            Long minPayPrice,
            Long maxDiscountPrice
    ) {
        return couponPolicy(
                discountType,
                discountPrice,
                minPayPrice,
                maxDiscountPrice,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(1)
        );
    }

    private CouponPolicy couponPolicy(
            DiscountType discountType,
            Long discountPrice,
            Long minPayPrice,
            Long maxDiscountPrice,
            LocalDateTime startDt,
            LocalDateTime endDt
    ) {
        CouponPolicy couponPolicy = new CouponPolicy();
        ReflectionTestUtils.setField(couponPolicy, "discountType", discountType);
        ReflectionTestUtils.setField(couponPolicy, "discountPrice", discountPrice);
        ReflectionTestUtils.setField(couponPolicy, "minPayPrice", minPayPrice);
        ReflectionTestUtils.setField(couponPolicy, "maxDiscountPrice", maxDiscountPrice);
        ReflectionTestUtils.setField(couponPolicy, "startDt", startDt);
        ReflectionTestUtils.setField(couponPolicy, "endDt", endDt);
        return couponPolicy;
    }
}
