package four_tential.potential.domain.payment.port;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class PaymentGatewayResponseTest {

    @Test
    @DisplayName("생성 시 pgKey 가 올바르게 저장된다")
    void constructor_pgKey() {
        PaymentGatewayResponse response = new PaymentGatewayResponse(
                "portone_key_123", "PAID", 100000L);
        assertThat(response.pgKey()).isEqualTo("portone_key_123");
    }

    @Test
    @DisplayName("생성 시 status 가 PAID 로 저장된다")
    void constructor_status_paid() {
        PaymentGatewayResponse response = new PaymentGatewayResponse(
                "portone_key_123", "PAID", 100000L);
        assertThat(response.status()).isEqualTo("PAID");
    }

    @Test
    @DisplayName("생성 시 status 가 FAILED 로 저장된다")
    void constructor_status_failed() {
        PaymentGatewayResponse response = new PaymentGatewayResponse(
                "portone_key_123", "FAILED", 0L);
        assertThat(response.status()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("생성 시 status 가 CANCELLED 로 저장된다")
    void constructor_status_cancelled() {
        PaymentGatewayResponse response = new PaymentGatewayResponse(
                "portone_key_123", "CANCELLED", 100000L);
        assertThat(response.status()).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("생성 시 totalAmount 가 올바르게 저장된다")
    void constructor_totalAmount() {
        PaymentGatewayResponse response = new PaymentGatewayResponse(
                "portone_key_123", "PAID", 100000L);
        assertThat(response.totalAmount()).isEqualTo(100000L);
    }

    @Test
    @DisplayName("동일한 값으로 생성한 두 Response 는 동등하다")
    void equals_same_values() {
        PaymentGatewayResponse response1 = new PaymentGatewayResponse(
                "portone_key_123", "PAID", 100000L);
        PaymentGatewayResponse response2 = new PaymentGatewayResponse(
                "portone_key_123", "PAID", 100000L);
        assertThat(response1).isEqualTo(response2);
    }

}