package four_tential.potential.common.exception.domain;

import four_tential.potential.common.exception.ServiceErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum CourseExceptionEnum implements ServiceErrorCode {
    ERR_INVALID_STATUS_TRANSITION_TO_CONFIRM(HttpStatus.BAD_REQUEST, "PREPARATION 상태의 코스만 개설 확정할 수 있습니다"),
    ERR_INVALID_STATUS_TRANSITION_TO_OPEN(HttpStatus.BAD_REQUEST, "PREPARATION 상태의 코스만 OPEN 할 수 있습니다"),
    ERR_INVALID_STATUS_TRANSITION_TO_CLOSE(HttpStatus.BAD_REQUEST, "OPEN 상태의 코스만 CLOSE 할 수 있습니다"),
    ERR_INVALID_STATUS_TRANSITION_TO_CANCEL(HttpStatus.BAD_REQUEST, "OPEN 상태의 코스만 취소할 수 있습니다"),
    ERR_INVALID_CAPACITY(HttpStatus.BAD_REQUEST, "코스의 정원은 최소 1명 이상이어야 합니다"),
    ERR_INVALID_ORDER_CLOSE_TIME(HttpStatus.BAD_REQUEST, "코스의 주문 마감 시간은 코스의 주문가능 시작 시각부터 코스의 시작일시 2시간 전 까지 가능합니다"),
    ERR_INVALID_SCHEDULE(HttpStatus.BAD_REQUEST, "코스의 종료 일시는 코스의 시작 일시보다 이후여야 합니다"),
    ERR_IMMUTABLE_FIELD_IN_OPEN(HttpStatus.BAD_REQUEST, "OPEN 상태에서는 가격, 일정, 장소, 정원을 수정할 수 없습니다"),
    ERR_CANNOT_MODIFY_COURSE(HttpStatus.BAD_REQUEST, "CLOSED 또는 CANCELLED 상태의 코스는 수정할 수 없습니다"),
    ERR_IS_FULL_CAPACITY(HttpStatus.CONFLICT, "코스의 정원이 가득차 추가할 수 없습니다");

    private final HttpStatus httpStatus;
    private final String message;

    CourseExceptionEnum(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
