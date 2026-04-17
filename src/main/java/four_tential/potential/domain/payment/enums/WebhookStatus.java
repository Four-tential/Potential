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
            return false;
        }
    },
    FAILED {
        @Override
        public boolean canTransitTo(WebhookStatus target) {
            return false;
        }
    };

    public abstract boolean canTransitTo(WebhookStatus target);
}
