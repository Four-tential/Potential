package four_tential.potential.domain.payment.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import four_tential.potential.domain.payment.enums.RefundStatus;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

import static four_tential.potential.domain.payment.entity.QRefund.refund;

@RequiredArgsConstructor
public class RefundCustomRepositoryImpl implements RefundCustomRepository{

    private final JPAQueryFactory queryFactory;

    @Override
    public Long sumRefundPriceByPaymentIdAndStatus(UUID paymentId, RefundStatus status) {
        Long result = queryFactory
                .select(refund.refundPrice.sumLong())
                .from(refund)
                .where(
                        refund.payment.id.eq(paymentId),
                        refund.status.eq(status)
                )
                .fetchOne();

        return result != null ? result : 0L;
    }
}
