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
    @DisplayName("Record 생성자로 생성 시 pgKey 가 올바르게 저장된다")
    void constructor_pgKey() {
        PaymentGatewayRequest request = new PaymentGatewayRequest(
                "portone_key_123", 100000L, "INSTRUCTOR");
        assertThat(request.pgKey()).isEqualTo("portone_key_123");
    }

    @Test
    @DisplayName("Record 생성자로 생성 시 amount 가 올바르게 저장된다")
    void constructor_amount() {
        PaymentGatewayRequest request = new PaymentGatewayRequest(
                "portone_key_123", 100000L, "INSTRUCTOR");
        assertThat(request.amount()).isEqualTo(100000L);
    }

    @Test
    @DisplayName("Record 생성자로 생성 시 reason 이 올바르게 저장된다")
    void constructor_reason() {
        PaymentGatewayRequest request = new PaymentGatewayRequest(
                "portone_key_123", 100000L, "INSTRUCTOR");
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

}