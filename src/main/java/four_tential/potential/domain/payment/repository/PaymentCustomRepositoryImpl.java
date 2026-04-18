package four_tential.potential.domain.payment.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import four_tential.potential.domain.payment.entity.Payment;
import four_tential.potential.presentation.payment.dto.PaymentDetailResponse;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;

import java.util.Optional;
import java.util.UUID;

import static four_tential.potential.domain.order.QOrder.order;
import static four_tential.potential.domain.payment.entity.QPayment.payment;

@RequiredArgsConstructor
public class PaymentCustomRepositoryImpl implements PaymentCustomRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<Payment> findByPgKey(String pgKey) {
        return Optional.ofNullable(
                queryFactory.selectFrom(payment)
                        .where(payment.pgKey.eq(pgKey))
                        .fetchOne()
        );
    }

    @Override
    public Optional<Payment> findByOrderId(UUID orderId) {
        return Optional.ofNullable(
                queryFactory.selectFrom(payment)
                        .where(payment.orderId.eq(orderId))
                        .fetchOne()
        );
    }

    @Override
    public Optional<Payment> findByPgKeyForUpdate(String pgKey) {
        return Optional.ofNullable(
                queryFactory.selectFrom(payment)
                        .where(payment.pgKey.eq(pgKey))
                        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                        .fetchOne()
        );
    }

    @Override
    public boolean existsByOrderId(UUID orderId) {
        Integer exists = queryFactory.selectOne()
                .from(payment)
                .where(payment.orderId.eq(orderId))
                .fetchFirst();

        return exists != null;
    }

    @Override
    public Optional<PaymentDetailResponse> findDetailByIdAndMemberId(UUID paymentId, UUID memberId) {
        PaymentDetailResponse result = queryFactory
                .select(Projections.constructor(PaymentDetailResponse.class,
                        payment.id,
                        payment.orderId,
                        order.titleSnap,
                        order.orderCount,
                        payment.totalPrice,
                        payment.discountPrice,
                        payment.paidTotalPrice,
                        payment.payWay,
                        payment.status,
                        payment.paidAt
                ))
                .from(payment)
                .join(order).on(order.id.eq(payment.orderId))
                .where(
                        payment.id.eq(paymentId),
                        payment.memberId.eq(memberId)
                )
                .fetchOne();

        return Optional.ofNullable(result);
    }
}
