package four_tential.potential.domain.payment.port;

public record PaymentGatewayResponse(String pgKey, String status, Long totalAmount, String payMethod) {

}
