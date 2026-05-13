package four_tential.potential.domain.payment.port;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class PaymentGatewayRequestTest {

    @Test
    @DisplayName("of 메서드로 생성 시 pgKey 가 올바르게 저장된다")
    void of_pgKey() {
        PaymentGatewayRequest request = PaymentGatewayRequest.of(
                "portone_key_123", 50000L, "CANCEL");
        assertThat(request.pgKey()).isEqualTo("portone_key_123");
    }

    @Test
    @DisplayName("of 메서드로 생성 시 amount 가 올바르게 저장된다")
    void of_amount() {
        PaymentGatewayRequest request = PaymentGatewayRequest.of(
                "portone_key_123", 50000L, "CANCEL");
        assertThat(request.amount()).isEqualTo(50000L);
    }

    @Test
    @DisplayName("of 메서드로 생성 시 reason 이 올바르게 저장된다")
    void of_reason() {
        PaymentGatewayRequest request = PaymentGatewayRequest.of(
                "portone_key_123", 50000L, "CANCEL");
        assertThat(request.reason()).isEqualTo("CANCEL");
    }

    @Test
    @DisplayName("of 메서드로 생성 시 currentCancellableAmount 는 amount 와 동일하다")
    void of_currentCancellableAmount_equals_amount() {
        PaymentGatewayRequest request = PaymentGatewayRequest.of(
                "portone_key_123", 50000L, "CANCEL");
        // 전액 취소 시 취소 가능 잔액 = 취소할 금액
        assertThat(request.currentCancellableAmount()).isEqualTo(50000L);
    }

    @Test
    @DisplayName("ofPartial 메서드로 생성 시 pgKey 가 올바르게 저장된다")
    void ofPartial_pgKey() {
        PaymentGatewayRequest request = PaymentGatewayRequest.ofPartial(
                "portone_key_123", 25000L, 75000L, "CANCEL");
        assertThat(request.pgKey()).isEqualTo("portone_key_123");
    }

    @Test
    @DisplayName("ofPartial 메서드로 생성 시 amount(이번 취소 금액) 가 올바르게 저장된다")
    void ofPartial_amount() {
        PaymentGatewayRequest request = PaymentGatewayRequest.ofPartial(
                "portone_key_123", 25000L, 75000L, "CANCEL");
        assertThat(request.amount()).isEqualTo(25000L);
    }

    @Test
    @DisplayName("ofPartial 메서드로 생성 시 currentCancellableAmount(잔여 취소 가능 금액) 가 올바르게 저장된다")
    void ofPartial_currentCancellableAmount() {
        PaymentGatewayRequest request = PaymentGatewayRequest.ofPartial(
                "portone_key_123", 25000L, 75000L, "CANCEL");
        // 부분 취소 시 취소할 금액과 잔여 취소 가능 금액은 다를 수 있다
        assertThat(request.currentCancellableAmount()).isEqualTo(75000L);
    }

    @Test
    @DisplayName("ofPartial 메서드로 생성 시 reason 이 올바르게 저장된다")
    void ofPartial_reason() {
        PaymentGatewayRequest request = PaymentGatewayRequest.ofPartial(
                "portone_key_123", 25000L, 75000L, "CANCEL");
        assertThat(request.reason()).isEqualTo("CANCEL");
    }

    @Test
    @DisplayName("부분 취소 시 amount 와 currentCancellableAmount 는 서로 다를 수 있다")
    void ofPartial_amount_and_currentCancellableAmount_can_differ() {
        Long cancelAmount     = 25000L;
        Long remainingAmount  = 75000L;  // 이전 환불 없다고 가정 → 원래 paidTotalPrice

        PaymentGatewayRequest request = PaymentGatewayRequest.ofPartial(
                "portone_key_123", cancelAmount, remainingAmount, "CANCEL");

        assertThat(request.amount()).isNotEqualTo(request.currentCancellableAmount());
        assertThat(request.amount()).isEqualTo(25000L);
        assertThat(request.currentCancellableAmount()).isEqualTo(75000L);
    }

    @Test
    @DisplayName("Record 생성자로 생성 시 currentCancellableAmount 가 올바르게 저장된다")
    void constructor_currentCancellableAmount() {
        PaymentGatewayRequest request = new PaymentGatewayRequest(
                "portone_key_123", 100000L, 100000L, "INSTRUCTOR");
        assertThat(request.currentCancellableAmount()).isEqualTo(100000L);
    }

    @Test
    @DisplayName("Record 생성자로 생성 시 reason 이 올바르게 저장된다")
    void constructor_reason() {
        PaymentGatewayRequest request = new PaymentGatewayRequest(
                "portone_key_123", 100000L, 100000L, "INSTRUCTOR");
        assertThat(request.reason()).isEqualTo("INSTRUCTOR");
    }

    @Test
    @DisplayName("동일한 값으로 생성한 두 Request 는 동등하다")
    void equals_same_values() {
        PaymentGatewayRequest request1 = PaymentGatewayRequest.of(
                "portone_key_123", 50000L, "CANCEL");
        PaymentGatewayRequest request2 = PaymentGatewayRequest.of(
                "portone_key_123", 50000L, "CANCEL");
        assertThat(request1).isEqualTo(request2);
    }

    @Test
    @DisplayName("ofPartial 로 생성한 동일한 값의 두 Request 는 동등하다")
    void ofPartial_equals_same_values() {
        PaymentGatewayRequest r1 = PaymentGatewayRequest.ofPartial(
                "portone_key_123", 25000L, 75000L, "CANCEL");
        PaymentGatewayRequest r2 = PaymentGatewayRequest.ofPartial(
                "portone_key_123", 25000L, 75000L, "CANCEL");
        assertThat(r1).isEqualTo(r2);
    }

}