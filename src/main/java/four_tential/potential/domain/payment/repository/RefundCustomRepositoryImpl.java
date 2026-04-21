package four_tential.potential.domain.payment.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import four_tential.potential.domain.payment.enums.RefundStatus;
import four_tential.potential.presentation.payment.dto.RefundDetailResponse;
import lombok.RequiredArgsConstructor;

import java.util.Optional;
import java.util.UUID;

import static four_tential.potential.domain.order.QOrder.order;
import static four_tential.potential.domain.payment.entity.QPayment.payment;
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

    @Override
    public Optional<RefundDetailResponse> findDetailByIdAndMemberId(UUID refundId, UUID memberId) {
        RefundDetailResponse result = queryFactory
                .select(Projections.constructor(RefundDetailResponse.class,
                        refund.id,
                        payment.id,
                        order.titleSnap,
                        refund.cancelCount,
                        refund.refundPrice,
                        refund.reason,
                        refund.status,
                        refund.refundedAt
                ))
                .from(refund)
                .join(refund.payment, payment)
                .join(order).on(order.id.eq(payment.orderId))
                .where(
                        refund.id.eq(refundId),
                        payment.memberId.eq(memberId)
                )
                .fetchOne();

        return Optional.ofNullable(result);
    }
}
