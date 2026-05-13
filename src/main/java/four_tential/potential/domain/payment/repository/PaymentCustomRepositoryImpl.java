package four_tential.potential.domain.payment.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import four_tential.potential.domain.payment.entity.Payment;
import four_tential.potential.domain.payment.enums.PaymentStatus;
import four_tential.potential.presentation.payment.dto.PaymentDetailResponse;
import four_tential.potential.presentation.payment.dto.PaymentListResponse;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
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

    @Override
    public Page<PaymentListResponse> findListByMemberIdAndStatus(
            UUID memberId, PaymentStatus status, Pageable pageable) {

        List<PaymentListResponse> content = queryFactory
                .select(Projections.constructor(PaymentListResponse.class,
                        payment.id,
                        payment.orderId,
                        order.titleSnap,
                        order.orderCount,
                        payment.paidTotalPrice,
                        payment.status,
                        payment.paidAt
                ))
                .from(payment)
                .join(order).on(order.id.eq(payment.orderId))
                .where(
                        payment.memberId.eq(memberId),
                        statusEq(status)
                )
                .orderBy(payment.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(payment.count())
                .from(payment)
                .where(
                        payment.memberId.eq(memberId),
                        statusEq(status)
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    /**
     * 환불 가능 여부 조회에 필요한 데이터를 payment JOIN order 1번 쿼리로 조회.
     * 개선 방식: 3번 개별 조회 -> 1번 JOIN 조회
     */
    @Override
    public Optional<RefundPreviewData> findRefundPreviewData(UUID paymentId, UUID memberId) {
        RefundPreviewData result = queryFactory
                .select(Projections.constructor(RefundPreviewData.class,
                        payment.id,
                        payment.memberId,
                        payment.paidTotalPrice,
                        payment.status.stringValue(),   // PaymentStatus.name()
                        order.courseId,
                        order.titleSnap,
                        order.orderCount,
                        order.priceSnap
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

    private BooleanExpression statusEq(PaymentStatus status) {
        return status != null ? payment.status.eq(status) : null;
    }
}
