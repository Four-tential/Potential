package four_tential.potential.infra.portone;

import four_tential.potential.domain.payment.port.PaymentGatewayRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

class PortOneClientTest {

    private PortOneClient portOneClient;

    @BeforeEach
    void setUp() {
        portOneClient = new PortOneClient();
        ReflectionTestUtils.setField(portOneClient, "apiSecret", "test-api-secret");
    }

    @Test
    @DisplayName("getPayment 호출 시 UnsupportedOperationException 이 발생한다")
    void getPayment_throws_unsupported_operation_exception() {
        assertThatThrownBy(() -> portOneClient.getPayment("portone_key_123"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("PortOne API 키 준비 후 구현 예정");
    }

    @Test
    @DisplayName("cancelPayment 호출 시 UnsupportedOperationException 이 발생한다")
    void cancelPayment_throws_unsupported_operation_exception() {
        PaymentGatewayRequest request = PaymentGatewayRequest.of(
                "portone_key_123", 50000L, "CANCEL");

        assertThatThrownBy(() -> portOneClient.cancelPayment(request))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("PortOne API 키 준비 후 구현 예정");
    }

}