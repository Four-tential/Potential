package four_tential.potential.domain.payment.port;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class PaymentGatewayResponseTest {

    @Test
    @DisplayName("PaymentGatewayResponse 생성 시 값이 올바르게 저장된다")
    void constructor_creates_response_correctly() {
        PaymentGatewayResponse response = new PaymentGatewayResponse(
                "portone_key_123", "PAID", 100000L);

        assertThat(response.pgKey()).isEqualTo("portone_key_123");
        assertThat(response.status()).isEqualTo("PAID");
        assertThat(response.totalAmount()).isEqualTo(100000L);
    }

    @Test
    @DisplayName("PAID 상태로 생성된 response 의 status 가 PAID 이다")
    void status_paid() {
        PaymentGatewayResponse response = new PaymentGatewayResponse(
                "portone_key_123", "PAID", 100000L);

        assertThat(response.status()).isEqualTo("PAID");
    }

    @Test
    @DisplayName("FAILED 상태로 생성된 response 의 status 가 FAILED 이다")
    void status_failed() {
        PaymentGatewayResponse response = new PaymentGatewayResponse(
                "portone_key_123", "FAILED", 0L);

        assertThat(response.status()).isEqualTo("FAILED");
    }

}