package four_tential.potential.common.exception.domain;

import four_tential.potential.common.exception.ServiceErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum CouponExceptionEnum implements ServiceErrorCode {

    // 회원 쿠폰
    ERR_COUPON_INVALID_STATUS_TRANSITION(HttpStatus.BAD_REQUEST, "변경할 수 없는 쿠폰 상태입니다");

    private final HttpStatus httpStatus;
    private final String message;

    CouponExceptionEnum(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
