package four_tential.potential.domain.payment.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import four_tential.potential.domain.payment.enums.RefundStatus;
import four_tential.potential.presentation.payment.dto.RefundDetailResponse;
import four_tential.potential.presentation.payment.dto.RefundListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
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

    @Override
    public Page<RefundListResponse> findListByMemberIdAndStatus(
            UUID memberId, RefundStatus status, Pageable pageable) {

        List<RefundListResponse> content = queryFactory
                .select(Projections.constructor(RefundListResponse.class,
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
                        payment.memberId.eq(memberId),
                        statusEq(status)
                )
                .orderBy(refund.refundedAt.desc(), refund.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(refund.count())
                .from(refund)
                .join(refund.payment, payment)
                .where(
                        payment.memberId.eq(memberId),
                        statusEq(status)
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private BooleanExpression statusEq(RefundStatus status) {
        return status != null ? refund.status.eq(status) : null;
    }
}
