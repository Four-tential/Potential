package four_tential.potential.domain.coupon.enums;

/**
 * 쿠폰 할인 구분
 * FIX  : 정액 할인 (discount_price 단위 = 원)
 * RATE : 정률 할인 (discount_price 단위 = %, max_discount_price 가 상한선)
 */
public enum DiscountType {
    FIX,
    RATE
}
