package four_tential.potential.domain.payment.enums;

public enum PaymentStatus {
    PENDING {
        @Override
        public boolean canTransitTo(PaymentStatus target) {
            return target == PENDING || target == PAID || target == FAILED;
        }
    },
    PAID {
        @Override
        public boolean canTransitTo(PaymentStatus target) {
            return target == PAID || target == PART_REFUNDED || target == REFUNDED;
        }
    },
    PART_REFUNDED {
        @Override
        public boolean canTransitTo(PaymentStatus target) {
            return target == PART_REFUNDED || target == REFUNDED;
        }
    },
    REFUNDED {
        @Override
        public boolean canTransitTo(PaymentStatus target) {
            return target == REFUNDED;
        }
    },
    FAILED {
        @Override
        public boolean canTransitTo(PaymentStatus target) {
            return target == FAILED;
        }
    };

    public abstract boolean canTransitTo(PaymentStatus target);
}
