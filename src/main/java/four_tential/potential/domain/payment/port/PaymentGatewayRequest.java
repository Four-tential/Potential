package four_tential.potential.domain.payment.port;

public record PaymentGatewayRequest(String pgKey, Long amount, String reason) {

    public static PaymentGatewayRequest of( String pgKey, Long amount, String reason) {
        return new PaymentGatewayRequest(pgKey, amount, reason);
    }
}
