package four_tential.potential.application.payment.consts;

public final class PaymentWebhookConstants {

    public static final String EVENT_STATUS_UNKNOWN = "UNKNOWN";
    public static final String WEBHOOK_TRANSACTION_PAID = "WebhookTransactionPaid";
    public static final String WEBHOOK_TRANSACTION_FAILED = "WebhookTransactionFailed";
    public static final String WEBHOOK_TRANSACTION_CANCELLED = "WebhookTransactionCancelled";
    public static final String FAIL_REASON_WEBHOOK_SIGNATURE_INVALID = "WEBHOOK_SIGNATURE_INVALID";
    public static final String FAIL_REASON_WEBHOOK_BUSINESS_FAILED = "WEBHOOK_BUSINESS_FAILED";
    public static final String FAIL_REASON_WEBHOOK_UNEXPECTED_ERROR = "WEBHOOK_UNEXPECTED_ERROR";
    public static final String FAIL_REASON_PORTONE_CANCEL_FAILED = "PORTONE_CANCEL_FAILED";
    public static final String FAIL_REASON_PAYMENT_CREATE_REJECTED = "PAYMENT_CREATE_REJECTED";
    public static final String WEBHOOK_TRANSACTION_CANCELLED_CANCELLED = "WebhookTransactionCancelledCancelled";
    public static final String WEBHOOK_TRANSACTION_CANCELLED_PARTIAL_CANCELLED = "WebhookTransactionCancelledPartialCancelled";

    private PaymentWebhookConstants() {
    }
}
