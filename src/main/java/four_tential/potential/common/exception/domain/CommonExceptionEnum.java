package four_tential.potential.common.exception.domain;

import four_tential.potential.common.exception.ServiceErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum CommonExceptionEnum implements ServiceErrorCode {
    ERR_GET_DISTRIBUTED_LOCK_FAIL(HttpStatus.CONFLICT, "서버 오류 처리 실패, 락 획득에 실패하였습니다"),
    ERR_DISTRIBUTED_LOCK_KEY_NULL(HttpStatus.CONFLICT, "서버 오류 처리 실패, 락의 키는 비어있을 수 없습니다")
    ;

    private final HttpStatus httpStatus;
    private final String message;

    CommonExceptionEnum(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
