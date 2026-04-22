package four_tential.potential.common.exception.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentExceptionEnumTest {

    @Test
    @DisplayName("ERR_NOT_FOUND_PAYMENT returns NOT_FOUND")
    void err_not_found_payment_status() {
        assertThat(PaymentExceptionEnum.ERR_NOT_FOUND_PAYMENT.getHttpStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("ERR_NOT_FOUND_PAYMENT returns the correct message")
    void err_not_found_payment_message() {
        assertThat(PaymentExceptionEnum.ERR_NOT_FOUND_PAYMENT.getMessage())
                .isEqualTo("결제를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("ERR_ALREADY_PAID returns BAD_REQUEST")
    void err_already_paid_status() {
        assertThat(PaymentExceptionEnum.ERR_ALREADY_PAID.getHttpStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("ERR_PAYMENT_AMOUNT_MISMATCH returns BAD_REQUEST")
    void err_payment_amount_mismatch_status() {
        assertThat(PaymentExceptionEnum.ERR_PAYMENT_AMOUNT_MISMATCH.getHttpStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("ERR_PAYMENT_KEY_MISMATCH returns BAD_REQUEST")
    void err_payment_key_mismatch_status() {
        assertThat(PaymentExceptionEnum.ERR_PAYMENT_KEY_MISMATCH.getHttpStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("ERR_PAYMENT_KEY_MISMATCH returns the correct message")
    void err_payment_key_mismatch_message() {
        assertThat(PaymentExceptionEnum.ERR_PAYMENT_KEY_MISMATCH.getMessage())
                .isEqualTo("결제 식별자가 일치하지 않습니다.");
    }

    @Test
    @DisplayName("ERR_REFUND_NOT_ALLOWED returns CONFLICT")
    void err_refund_not_allowed_status() {
        assertThat(PaymentExceptionEnum.ERR_REFUND_NOT_ALLOWED.getHttpStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("ERR_WEBHOOK_SIGNATURE_INVALID returns BAD_REQUEST")
    void err_webhook_signature_invalid_status() {
        assertThat(PaymentExceptionEnum.ERR_WEBHOOK_SIGNATURE_INVALID.getHttpStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("ERR_PAYMENT_FORBIDDEN returns FORBIDDEN")
    void err_payment_forbidden_status() {
        assertThat(PaymentExceptionEnum.ERR_PAYMENT_FORBIDDEN.getHttpStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("ERR_REFUND_PROCESS_FAILED returns INTERNAL_SERVER_ERROR")
    void err_refund_process_failed_status() {
        assertThat(PaymentExceptionEnum.ERR_REFUND_PROCESS_FAILED.getHttpStatus())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("ERR_REFUND_PAYMENT_STATUS_INVALID returns BAD_REQUEST")
    void err_refund_payment_status_invalid_status() {
        assertThat(PaymentExceptionEnum.ERR_REFUND_PAYMENT_STATUS_INVALID.getHttpStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("ERR_NOT_FOUND_REFUND returns NOT_FOUND")
    void err_not_found_refund_status() {
        assertThat(PaymentExceptionEnum.ERR_NOT_FOUND_REFUND.getHttpStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("ERR_ALREADY_FULLY_REFUNDED returns BAD_REQUEST")
    void err_already_fully_refunded_status() {
        assertThat(PaymentExceptionEnum.ERR_ALREADY_FULLY_REFUNDED.getHttpStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
