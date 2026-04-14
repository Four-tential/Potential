package four_tential.potential.common.exception.domain;

import four_tential.potential.common.exception.ServiceErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum PaymentExceptionEnum implements ServiceErrorCode {

    // 결제
    ERR_NOT_FOUND_PAYMENT(HttpStatus.NOT_FOUND, "결제를 찾을 수 없습니다"),
    ERR_ALREADY_PAID(HttpStatus.BAD_REQUEST, "이미 결제가 완료된 주문입니다"),
    ERR_PAYMENT_DEADLINE_EXCEEDED(HttpStatus.BAD_REQUEST, "결제 가능 시간이 초과되었습니다"),
    ERR_PAYMENT_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "결제 금액이 일치하지 않습니다"),
    ERR_PAYMENT_FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다"),

    // 환불
    ERR_NOT_FOUND_REFUND(HttpStatus.NOT_FOUND, "환불 내역을 찾을 수 없습니다"),
    ERR_REFUND_NOT_ALLOWED(HttpStatus.CONFLICT, "환불이 불가한 기간입니다"),
    ERR_ALREADY_FULLY_REFUNDED(HttpStatus.BAD_REQUEST, "이미 전액 환불된 결제입니다"),
    ERR_CANCEL_COUNT_EXCEEDED(HttpStatus.BAD_REQUEST, "취소 가능 수량을 초과했습니다"),
    ERR_CANCEL_COUNT_INVALID(HttpStatus.BAD_REQUEST, "취소 수량이 올바르지 않습니다"),
    ERR_REFUND_PROCESS_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "환불 처리 중 오류가 발생했습니다"),

    // 웹훅
    ERR_WEBHOOK_SIGNATURE_INVALID(HttpStatus.BAD_REQUEST, "웹훅 서명 검증에 실패했습니다"),
    ERR_NOT_FOUND_WEBHOOK(HttpStatus.NOT_FOUND, "웹훅 내역을 찾을 수 없습니다")
    ;

    private final HttpStatus httpStatus;
    private final String message;

    PaymentExceptionEnum(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
