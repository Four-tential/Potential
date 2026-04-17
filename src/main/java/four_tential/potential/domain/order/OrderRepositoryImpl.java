package four_tential.potential.domain.order;

import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

import java.util.List;
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

    @Override
    public Page<Order> findMyOrders(UUID memberId, Pageable pageable) {
        List<Order> content = queryFactory.selectFrom(order)
                .where(order.memberId.eq(memberId))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(order.createdAt.desc(), order.id.desc())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory.select(order.count())
                .from(order)
                .where(order.memberId.eq(memberId));

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }
}
