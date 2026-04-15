package four_tential.potential.domain.order;

import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.util.Optional;
import java.util.UUID;

import static four_tential.potential.domain.order.QOrder.order;

@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<Order> findOrderDetailsById(UUID orderId, UUID memberId) {
        return Optional.ofNullable(
                queryFactory.selectFrom(order)
                        .where(
                                order.id.eq(orderId),
                                order.memberId.eq(memberId)
                        )
                        .fetchOne()
        );
    }
}
