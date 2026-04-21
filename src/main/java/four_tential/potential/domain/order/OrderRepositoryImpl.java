package four_tential.potential.domain.order;

import com.querydsl.core.types.Projections;
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

import static four_tential.potential.domain.attendance.QAttendance.attendance;
import static four_tential.potential.domain.course.course.QCourse.course;
import static four_tential.potential.domain.member.member.QMember.member;
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

    @Override
    public Long sumStudentCountByMemberInstructorIdAndStatusIn(
            UUID memberInstructorId,
            Collection<OrderStatus> statuses
    ) {
        Long studentCount = queryFactory
                .select(order.orderCount.sumLong())
                .from(order)
                .join(course).on(course.id.eq(order.courseId))
                .where(
                        course.memberInstructorId.eq(memberInstructorId),
                        order.status.in(statuses)
                )
                .fetchOne();

        return studentCount != null ? studentCount : 0L;
    }

    @Override
    public Page<CourseStudentQueryResult> findConfirmedStudentsByCourseId(UUID courseId, Pageable pageable) {
        List<CourseStudentQueryResult> content = queryFactory
                .select(Projections.constructor(CourseStudentQueryResult.class,
                        member.id,
                        member.name,
                        attendance.status,
                        attendance.attendanceAt
                ))
                .from(order)
                .join(member).on(member.id.eq(order.memberId))
                .leftJoin(attendance).on(
                        attendance.courseId.eq(order.courseId)
                                .and(attendance.memberId.eq(order.memberId))
                )
                .where(
                        order.courseId.eq(courseId),
                        order.status.eq(OrderStatus.CONFIRMED)
                )
                .orderBy(order.createdAt.asc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(order.count())
                .from(order)
                .where(
                        order.courseId.eq(courseId),
                        order.status.eq(OrderStatus.CONFIRMED)
                );

        return PageableExecutionUtils.getPage(content, pageable, () -> countQuery.fetchOne());
    }

    @Override
    public List<Order> findRefundableOrdersByCourseId(UUID courseId) {
        return queryFactory
                .selectFrom(order)
                .where(
                        order.courseId.eq(courseId),
                        order.status.in(OrderStatus.PAID, OrderStatus.CONFIRMED)
                )
                .fetch();
    }

    @Override
    public List<Order> findPaidOrdersToConfirm(LocalDateTime now, Pageable pageable) {
        return queryFactory.selectFrom(order)
                .join(course).on(course.id.eq(order.courseId))
                .where(
                        order.status.eq(OrderStatus.PAID),
                        course.startAt.lt(now.plusDays(7))
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
    }
}
