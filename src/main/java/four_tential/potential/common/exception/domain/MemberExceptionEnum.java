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
    ERR_TOKEN_NULL(HttpStatus.UNAUTHORIZED, "인증 정보가 비어있습니다"),
    ERR_INVALID_ONBOARD_GOAL(HttpStatus.BAD_REQUEST, "목표를 입력해주세요"),
    ERR_INVALID_CATEGORY(HttpStatus.BAD_REQUEST, "카테고리를 입력해주세요"),
    ERR_DUPLICATED_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일 입니다"),
    ERR_INVALID_STATUS_TRANSITION_TO_APPROVE(HttpStatus.BAD_REQUEST, "PENDING 상태의 신청 건만 승인 할 수 있습니다"),
    ERR_INVALID_STATUS_TRANSITION_TO_REJECT(HttpStatus.BAD_REQUEST, "PENDING 상태의 신청 건만 거절 할 수 있습니다"),
    ERR_BLANK_REJECT_REASON(HttpStatus.BAD_REQUEST, "거절 사유를 입력해주세요"),
    ERR_WITHDRAWAL_MEMBER(HttpStatus.FORBIDDEN, "탈퇴한 회원입니다"),
    ERR_NO_UPDATE_FIELD(HttpStatus.BAD_REQUEST, "수정할 항목을 하나 이상 입력해주세요"),
    ERR_ALREADY_ONBOARDED(HttpStatus.CONFLICT, "이미 온보딩을 완료한 회원입니다"),
    ERR_NOT_FOUND_ONBOARDING(HttpStatus.NOT_FOUND, "온보딩 정보가 존재하지 않습니다"),
    ERR_DUPLICATED_CATEGORY_IN_REQUEST(HttpStatus.BAD_REQUEST, "중복된 카테고리 코드가 포함되어 있습니다"),
    ERR_ALREADY_INSTRUCTOR(HttpStatus.CONFLICT, "이미 강사로 등록된 회원입니다"),
    ERR_ALREADY_IN_PROGRESS_APPLICATION(HttpStatus.CONFLICT, "이미 처리 중인 강사 신청이 있습니다"),
    ERR_NOT_FOUND_INSTRUCTOR_APPLICATION(HttpStatus.NOT_FOUND, "강사 신청 내역이 존재하지 않습니다"),
    ERR_NOT_AUTHORIZE_TO_INSTRUCTOR(HttpStatus.FORBIDDEN, "일반 회원 외엔 강사 신청을 할 수 없습니다"),
    ERR_ALREADY_PROCESSED_APPLICATION(HttpStatus.CONFLICT, "이미 처리된 강사 신청입니다"),
    ERR_WRONG_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "현재 비밀번호가 올바르지 않습니다"),
    ERR_SAME_AS_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "현재 비밀번호와 동일한 비밀번호로 변경할 수 없습니다"),
    ERR_INVALID_MEMBER_STATUS_TRANSITION(HttpStatus.BAD_REQUEST, "잘못된 상태 전환입니다, ACTIVE와 SUSPENDED 간의 전환만 가능합니다"),
    ERR_WRONG_PASSWORD(HttpStatus.BAD_REQUEST, "비밀번호가 올바르지 않습니다"),
    ERR_HAS_COURSE(HttpStatus.CONFLICT, "수강해야할 코스가 있어 탈퇴가 불가합니다"),
    ERR_HAS_ACTIVE_INSTRUCTOR_COURSES(HttpStatus.CONFLICT, "진행 중인 코스가 있어 탈퇴가 불가합니다"),
    ERR_NOT_FOUND_INSTRUCTOR(HttpStatus.NOT_FOUND, "존재하지 않는 강사입니다"),
    ERR_CANNOT_FOLLOW_SELF(HttpStatus.BAD_REQUEST, "본인을 팔로우 할 수 없습니다"),
    ERR_ALREADY_FOLLOWED(HttpStatus.CONFLICT, "이미 팔로우한 강사입니다")
    ;

    private final HttpStatus httpStatus;
    private final String message;

    MemberExceptionEnum(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
