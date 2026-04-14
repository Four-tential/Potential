package four_tential.potential.domain.payment.port;

public interface PaymentGateway {

    // 결제 정보 조회
    PaymentGatewayResponse getPayment(String pgKey);

    // 결제 취소 (환불)
    void cancelPayment(PaymentGatewayRequest request);
}
