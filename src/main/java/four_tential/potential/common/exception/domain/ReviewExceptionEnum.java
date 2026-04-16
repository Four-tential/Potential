package four_tential.potential.common.exception.domain;

import four_tential.potential.common.exception.ServiceErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ReviewExceptionEnum implements ServiceErrorCode {

    // 작성 조건 관련
    ERR_ORDER_NOT_CONFIRMED(HttpStatus.FORBIDDEN, "예약이 확정된 상태의 코스만 후기를 작성할 수 있습니다"),
    ERR_COURSE_NOT_CLOSED(HttpStatus.FORBIDDEN, "종료된 코스만 후기를 작성할 수 있습니다"),
    ERR_REVIEW_PERIOD_EXPIRED(HttpStatus.FORBIDDEN, "후기 작성 기간이 지났습니다 (코스 종료 후 7일 이내)"),
    ERR_NOT_ATTENDED(HttpStatus.FORBIDDEN, "출석한 수강생만 후기를 작성할 수 있습니다"),
    ERR_ALREADY_REVIEWED(HttpStatus.CONFLICT, "이미 후기를 작성한 코스입니다"),

    // 권한 관련
    ERR_REVIEW_FORBIDDEN(HttpStatus.FORBIDDEN, "해당 후기에 대한 권한이 없습니다"),

    // 좋아요 관련
    ERR_SELF_LIKE_FORBIDDEN(HttpStatus.FORBIDDEN, "자신의 후기에는 좋아요를 누를 수 없습니다"),

    // 조회 관련
    ERR_REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "후기를 찾을 수 없습니다"),
    ;

    private final HttpStatus httpStatus;
    private final String message;

    ReviewExceptionEnum(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}