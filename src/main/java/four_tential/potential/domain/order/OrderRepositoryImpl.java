package four_tential.potential.domain.order;

import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import four_tential.potential.domain.course.course.CourseStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static four_tential.potential.domain.course.course.QCourse.course;
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

    @Override
    public boolean existsActiveEnrollment(
            UUID memberId,
            Collection<OrderStatus> orderStatuses,
            Collection<CourseStatus> courseStatuses,
            LocalDateTime now
    ) {
        Integer exists = queryFactory.selectOne()
                .from(order)
                .join(course).on(course.id.eq(order.courseId))
                .where(
                        order.memberId.eq(memberId),
                        order.status.in(orderStatuses),
                        course.status.in(courseStatuses),
                        course.endAt.after(now)
                )
                .fetchFirst();

        return exists != null;
    }
}
