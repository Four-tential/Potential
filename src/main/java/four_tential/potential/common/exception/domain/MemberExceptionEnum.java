package four_tential.potential.common.exception.domain;

import four_tential.potential.common.exception.ServiceErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum MemberExceptionEnum implements ServiceErrorCode {
    ERR_NOT_FOUND_MEMBER(HttpStatus.NOT_FOUND, "존재하지 않는 회원입니다"),
    ERR_WRONG_LOGIN(HttpStatus.NOT_FOUND, "아이디와 비밀번호를 확인하시기 바랍니다"),
    ERR_SUSPENDED(HttpStatus.FORBIDDEN, "정지된 회원입니다, 관리자에게 문의 바랍니다"),
    ERR_INVALID_MEMBER(HttpStatus.BAD_REQUEST, "회원을 입력해주세요"),
    ERR_INVALID_AUTHORIZE(HttpStatus.UNAUTHORIZED, "잘못된 인증 정보입니다, 다시 로그인 하시기 바랍니다"),
    ERR_REFRESH_TOKEN_NULL(HttpStatus.UNAUTHORIZED, "잘못된 인증 정보입니다, 다시 로그인 하시기 바랍니다"),
    ERR_INVALID_ONBOARD_GOAL(HttpStatus.BAD_REQUEST, "목표를 입력해주세요"),
    ERR_INVALID_CATEGORY(HttpStatus.BAD_REQUEST, "카테고리를 입력해주세요"),
    ERR_DUPLICATED_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일 입니다"),
    ERR_INVALID_STATUS_TRANSITION_TO_APPROVE(HttpStatus.BAD_REQUEST, "PENDING 상태의 신청 건만 승인 할 수 있습니다"),
    ERR_INVALID_STATUS_TRANSITION_TO_REJECT(HttpStatus.BAD_REQUEST, "PENDING 상태의 신청 건만 거절 할 수 있습니다"),
    ERR_BLANK_REJECT_REASON(HttpStatus.BAD_REQUEST, "거절 사유를 입력해주세요")
    ;

    private final HttpStatus httpStatus;
    private final String message;

    MemberExceptionEnum(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
