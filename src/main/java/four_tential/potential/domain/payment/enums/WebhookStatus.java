package four_tential.potential.domain.payment.enums;

public enum WebhookStatus {
    PENDING {
        @Override
        public boolean canTransitTo(WebhookStatus target) {
            return target == PENDING || target == COMPLETED || target == FAILED;
        }
    },
    COMPLETED {
        @Override
        public boolean canTransitTo(WebhookStatus target) {
            return target == COMPLETED;
        }
    },
    FAILED {
        @Override
        public boolean canTransitTo(WebhookStatus target) {
            return target == FAILED || target == PENDING;
        }
    };

    public abstract boolean canTransitTo(WebhookStatus target);
}
