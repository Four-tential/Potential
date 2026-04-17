package four_tential.potential.application.payment.consts;

public final class PaymentWebhookConstants {

    public static final String EVENT_STATUS_UNKNOWN = "UNKNOWN";
    public static final String WEBHOOK_TRANSACTION_PAID = "WebhookTransactionPaid";
    public static final String WEBHOOK_TRANSACTION_FAILED = "WebhookTransactionFailed";
    public static final String WEBHOOK_TRANSACTION_CANCELLED = "WebhookTransactionCancelled";

    private PaymentWebhookConstants() {
    }
}
