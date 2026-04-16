package four_tential.potential.domain.payment.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import four_tential.potential.domain.payment.entity.Payment;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;

import java.util.Optional;
import java.util.UUID;

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
}
