package four_tential.potential.common.exception.domain;

import four_tential.potential.common.exception.ServiceErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum CouponExceptionEnum implements ServiceErrorCode {

    // 쿠폰 정책
    ERR_COUPON_MIN_PRICE_NOT_MET(HttpStatus.BAD_REQUEST, "최소 결제 금액을 충족하지 않아 쿠폰을 적용할 수 없습니다"),

    // 회원 쿠폰
    ERR_COUPON_INVALID_STATUS_TRANSITION(HttpStatus.BAD_REQUEST, "변경할 수 없는 쿠폰 상태입니다"),
    ERR_MEMBER_COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "보유한 쿠폰을 찾을 수 없습니다"),
    ERR_COUPON_NOT_USABLE(HttpStatus.BAD_REQUEST, "사용할 수 없는 쿠폰입니다");

    private final HttpStatus httpStatus;
    private final String message;

    CouponExceptionEnum(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
