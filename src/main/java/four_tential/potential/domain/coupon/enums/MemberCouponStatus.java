package four_tential.potential.domain.coupon.enums;

public enum MemberCouponStatus {
    ISSUED {
        @Override
        public boolean canTransitTo(MemberCouponStatus target) {
            return target == USED || target == EXPIRED;
        }
    },
    USED {
        @Override
        public boolean canTransitTo(MemberCouponStatus target) {
            // 결제 취소 시 ISSUED 로 복구할 수 있도록 허용
            return target == ISSUED;
        }
    },
    EXPIRED {
        @Override
        public boolean canTransitTo(MemberCouponStatus target) {
            return false;
        }
    };

    public abstract boolean canTransitTo(MemberCouponStatus target);
}
