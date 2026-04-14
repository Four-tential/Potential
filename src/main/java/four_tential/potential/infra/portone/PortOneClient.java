package four_tential.potential.infra.portone;

import four_tential.potential.domain.payment.port.PaymentGateway;
import four_tential.potential.domain.payment.port.PaymentGatewayRequest;
import four_tential.potential.domain.payment.port.PaymentGatewayResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PortOneClient implements PaymentGateway {

    @Value("${portone.api-secret}")
    private String apiSecret;

    @Override
    public PaymentGatewayResponse getPayment(String pgKey) {
        // TODO: PortOne API 키 준비 후 구현
        return null;
    }

    @Override
    public void cancelPayment(PaymentGatewayRequest request) {
        // TODO: PortOne API 키 준비 후 구현
    }
}
