package four_tential.potential.common.exception.domain;

import four_tential.potential.common.exception.ServiceErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum AttendanceExceptionEnum implements ServiceErrorCode {

    // QR 생성 관련
    ERR_QR_FORBIDDEN(HttpStatus.FORBIDDEN, "본인 코스에 대해서만 QR을 생성할 수 있습니다"),
    ERR_QR_NOT_STARTED(HttpStatus.FORBIDDEN, "클래스 시작 후에만 QR을 생성할 수 있습니다"),
    ERR_QR_EXPIRED_WINDOW(HttpStatus.FORBIDDEN, "QR 생성 가능 시간이 초과되었습니다 (클래스 시작 후 10분 이내)"),
    ERR_QR_ALREADY_ACTIVE(HttpStatus.CONFLICT, "이미 유효한 QR 코드가 존재합니다"),

    // QR 스캔 관련
    ERR_QR_INVALID_FORMAT(HttpStatus.BAD_REQUEST, "유효하지 않은 QR 토큰 형식입니다"),
    ERR_QR_NOT_FOUND(HttpStatus.NOT_FOUND, "QR 토큰이 존재하지 않습니다"),
    ERR_QR_EXPIRED(HttpStatus.REQUEST_TIMEOUT, "QR 코드 유효시간이 만료되었습니다"),
    ERR_ALREADY_CHECKED(HttpStatus.CONFLICT, "이미 출석 처리된 수강생입니다"),
    ERR_SCAN_ONLY_STUDENT(HttpStatus.CONFLICT, "학생만 QR 스캔할 수 있습니다"),

    // 출석 권한 관련
    ERR_ORDER_NOT_CONFIRMED(HttpStatus.FORBIDDEN, "예약이 확정된 수강생만 출석할 수 있습니다"),
    ERR_NOT_ENROLLED(HttpStatus.FORBIDDEN, "해당 코스에 등록된 수강생이 아닙니다"),
    ERR_ATTENDANCE_FORBIDDEN(HttpStatus.FORBIDDEN, "출석 현황을 조회할 권한이 없습니다"),

    // 출석 조회 관련
    ERR_NOT_FOUND_ATTENDANCE(HttpStatus.NOT_FOUND, "출석 정보를 찾을 수 없습니다"),
    ;

    private final HttpStatus httpStatus;
    private final String message;

    AttendanceExceptionEnum(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}