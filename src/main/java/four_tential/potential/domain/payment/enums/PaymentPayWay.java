package four_tential.potential.domain.payment.enums;

public enum PaymentPayWay {
    CARD,
    EASY_PAY;

    /**
     * PortOne payMethod 값을 PaymentPayWay 로 변환
     *
     * @param portOnePayMethod PortOne 결제 수단 값
     * @return PaymentPayWay
     */
    public static PaymentPayWay from(String portOnePayMethod) {
        return switch (portOnePayMethod) {
            case "card" -> CARD;
            case "easyPay" -> EASY_PAY;
            default -> CARD;  // 알 수 없는 수단은 CARD 로 기본 처리
        };
    }
}
