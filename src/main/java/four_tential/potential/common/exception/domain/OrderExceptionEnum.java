package four_tential.potential.common.exception.domain;

import four_tential.potential.common.exception.ServiceErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum OrderExceptionEnum implements ServiceErrorCode {

    ERR_NOT_FOUND_ORDER(HttpStatus.NOT_FOUND, "주문 정보를 찾을 수 없습니다"),
    ERR_ALREADY_RESERVED(HttpStatus.CONFLICT, "동일한 시간대에 이미 예약된 코스가 있습니다"),
    ERR_NO_AVAILABLE_SEATS(HttpStatus.BAD_REQUEST, "코스의 잔여석이 없습니다"),
    ERR_INVALID_ORDER_STATUS(HttpStatus.BAD_REQUEST, "변경할 수 없는 주문 상태입니다"),
    ERR_CANNOT_CANCEL_ORDER(HttpStatus.BAD_REQUEST, "코스 시작 7일 전까지만 취소가 가능합니다"),
    ERR_ORDER_EXPIRED(HttpStatus.BAD_REQUEST, "결제 가능 시간이 초과되었습니다"),
    ERR_QUEUE_FULL(HttpStatus.SERVICE_UNAVAILABLE, "현재 대기열이 가득 차서 신청할 수 없습니다"),
    ERR_DUPLICATE_ORDER(HttpStatus.BAD_REQUEST, "이미 해당 강의를 주문 중이거나 대기 중입니다"),
    ERR_LOCK_INTERRUPTED(HttpStatus.INTERNAL_SERVER_ERROR, "락 획득 중 인터럽트가 발생했습니다"),
    ERR_LOCK_ACQUISITION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "락을 획득하지 못했습니다")
    ;

    private final HttpStatus httpStatus;
    private final String message;

    OrderExceptionEnum(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
