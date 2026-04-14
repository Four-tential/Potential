package four_tential.potential.domain.payment.port;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class PaymentGatewayRequestTest {

    @Test
    @DisplayName("of 메서드로 PaymentGatewayRequest 생성 시 값이 올바르게 저장된다")
    void of_creates_request_correctly() {
        PaymentGatewayRequest request = PaymentGatewayRequest.of(
                "portone_key_123", 50000L, "CANCEL");

        assertThat(request.pgKey()).isEqualTo("portone_key_123");
        assertThat(request.amount()).isEqualTo(50000L);
        assertThat(request.reason()).isEqualTo("CANCEL");
    }

    @Test
    @DisplayName("Record 생성자로 PaymentGatewayRequest 생성 시 값이 올바르게 저장된다")
    void constructor_creates_request_correctly() {
        PaymentGatewayRequest request = new PaymentGatewayRequest(
                "portone_key_123", 100000L, "INSTRUCTOR");

        assertThat(request.pgKey()).isEqualTo("portone_key_123");
        assertThat(request.amount()).isEqualTo(100000L);
        assertThat(request.reason()).isEqualTo("INSTRUCTOR");
    }

}