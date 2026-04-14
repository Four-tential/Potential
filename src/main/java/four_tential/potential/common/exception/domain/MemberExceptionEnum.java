package four_tential.potential.common.exception.domain;

import four_tential.potential.common.exception.ServiceErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum MemberExceptionEnum implements ServiceErrorCode {
    ERR_UNAUTHORIZED(HttpStatus.NOT_FOUND, "존재하지 않는 회원입니다"),
    ERR_INVALID_MEMBER(HttpStatus.BAD_REQUEST, "회원을 입력해주세요"),
    ERR_INVALID_ONBOARD_GOAL(HttpStatus.BAD_REQUEST, "목표를 입력해주세요"),
    ERR_INVALID_CATEGORY(HttpStatus.BAD_REQUEST, "카테고리를 입력해주세요")
    ;

    private final HttpStatus httpStatus;
    private final String message;

    MemberExceptionEnum(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
