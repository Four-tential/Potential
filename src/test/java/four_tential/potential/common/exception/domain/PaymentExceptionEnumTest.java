package four_tential.potential.common.exception.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class PaymentExceptionEnumTest {

    @Test
    @DisplayName("ERR_NOT_FOUND_PAYMENT 는 404 상태코드를 반환한다")
    void not_found_payment_status() {
        assertThat(PaymentExceptionEnum.ERR_NOT_FOUND_PAYMENT.getHttpStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("ERR_NOT_FOUND_PAYMENT 는 올바른 메시지를 반환한다")
    void not_found_payment_message() {
        assertThat(PaymentExceptionEnum.ERR_NOT_FOUND_PAYMENT.getMessage())
                .isEqualTo("결제를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("ERR_ALREADY_PAID 는 400 상태코드를 반환한다")
    void already_paid_status() {
        assertThat(PaymentExceptionEnum.ERR_ALREADY_PAID.getHttpStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("ERR_REFUND_NOT_ALLOWED 는 409 상태코드를 반환한다")
    void refund_not_allowed_status() {
        assertThat(PaymentExceptionEnum.ERR_REFUND_NOT_ALLOWED.getHttpStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("ERR_PAYMENT_FORBIDDEN 은 403 상태코드를 반환한다")
    void payment_forbidden_status() {
        assertThat(PaymentExceptionEnum.ERR_PAYMENT_FORBIDDEN.getHttpStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("ERR_REFUND_PROCESS_FAILED 는 500 상태코드를 반환한다")
    void refund_process_failed_status() {
        assertThat(PaymentExceptionEnum.ERR_REFUND_PROCESS_FAILED.getHttpStatus())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("ERR_WEBHOOK_SIGNATURE_INVALID 는 400 상태코드를 반환한다")
    void webhook_signature_invalid_status() {
        assertThat(PaymentExceptionEnum.ERR_WEBHOOK_SIGNATURE_INVALID.getHttpStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}