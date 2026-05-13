package four_tential.potential.domain.member.instructor_member;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import four_tential.potential.domain.member.member.MemberStatus;
import four_tential.potential.domain.order.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static four_tential.potential.domain.course.course.QCourse.course;
import static four_tential.potential.domain.course.course_category.QCourseCategory.courseCategory;
import static four_tential.potential.domain.member.instructor_member.QInstructorMember.instructorMember;
import static four_tential.potential.domain.member.member.QMember.member;
import static four_tential.potential.domain.order.QOrder.order;
import static four_tential.potential.domain.review.review.QReview.review;

@RequiredArgsConstructor
public class InstructorMemberQueryRepositoryImpl implements InstructorMemberQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<InstructorApplicationItemResult> findInstructorApplications(InstructorMemberStatus status, Pageable pageable) {
        List<InstructorApplicationItemResult> content = queryFactory
                .select(Projections.constructor(InstructorApplicationItemResult.class,
                        member.id,
                        member.name,
                        member.email,
                        instructorMember.categoryCode,
                        courseCategory.name,
                        instructorMember.status,
                        instructorMember.createdAt
                ))
                .from(instructorMember)
                .join(member).on(member.id.eq(instructorMember.memberId))
                .join(courseCategory).on(courseCategory.code.eq(instructorMember.categoryCode))
                .where(statusEq(status))
                .orderBy(instructorMember.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(instructorMember.count())
                .from(instructorMember)
                .where(statusEq(status))
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0);
    }

    @Override
    public Optional<InstructorApplicationDetailResult> findInstructorApplicationDetail(UUID memberId) {
        return Optional.ofNullable(
                queryFactory
                        .select(Projections.constructor(InstructorApplicationDetailResult.class,
                                member.id,
                                member.name,
                                member.email,
                                member.phone,
                                instructorMember.categoryCode,
                                courseCategory.name,
                                instructorMember.content,
                                instructorMember.imageUrl,
                                instructorMember.status,
                                instructorMember.rejectReason,
                                instructorMember.createdAt,
                                instructorMember.respondedAt
                        ))
                        .from(instructorMember)
                        .join(member).on(member.id.eq(instructorMember.memberId))
                        .join(courseCategory).on(courseCategory.code.eq(instructorMember.categoryCode))
                        .where(instructorMember.memberId.eq(memberId))
                        .fetchOne()
        );
    }

    @Override
    public Optional<MyInstructorApplicationResult> findMyInstructorApplication(UUID memberId) {
        return Optional.ofNullable(
                queryFactory
                        .select(Projections.constructor(MyInstructorApplicationResult.class,
                                instructorMember.categoryCode,
                                courseCategory.name,
                                instructorMember.content,
                                instructorMember.imageUrl,
                                instructorMember.status,
                                instructorMember.rejectReason,
                                instructorMember.createdAt,
                                instructorMember.respondedAt
                        ))
                        .from(instructorMember)
                        .join(courseCategory).on(courseCategory.code.eq(instructorMember.categoryCode))
                        .where(instructorMember.memberId.eq(memberId))
                        .fetchOne()
        );
    }

    @Override
    public Optional<InstructorProfileQueryResult> findInstructorProfile(UUID memberId) {
        return Optional.ofNullable(
                queryFactory
                        .select(Projections.constructor(InstructorProfileQueryResult.class,
                                member.id,
                                member.name,
                                instructorMember.imageUrl,
                                instructorMember.categoryCode,
                                courseCategory.name,
                                instructorMember.content,
                                JPAExpressions.select(course.count())
                                        .from(course)
                                        .where(course.memberInstructorId.eq(instructorMember.id)),
                                Expressions.asNumber(
                                        JPAExpressions.select(review.rating.avg().coalesce(0.0))
                                                .from(review)
                                                .join(course).on(course.id.eq(review.courseId))
                                                .where(course.memberInstructorId.eq(instructorMember.id))
                                ).doubleValue(),
                                Expressions.asNumber(
                                        JPAExpressions.select(order.orderCount.sumLong().coalesce(0L))
                                                .from(order)
                                                .join(course).on(course.id.eq(order.courseId))
                                                .where(
                                                        course.memberInstructorId.eq(instructorMember.id),
                                                        order.status.in(OrderStatus.PAID, OrderStatus.CONFIRMED)
                                                )
                                ).longValue()
                        ))
                        .from(instructorMember)
                        .join(member).on(member.id.eq(instructorMember.memberId))
                        .join(courseCategory).on(courseCategory.code.eq(instructorMember.categoryCode))
                        .where(
                                instructorMember.memberId.eq(memberId),
                                instructorMember.status.eq(InstructorMemberStatus.APPROVED),
                                member.status.eq(MemberStatus.ACTIVE)
                        )
                        .fetchOne()
        );
    }

    private BooleanExpression statusEq(InstructorMemberStatus status) {
        return status != null ? instructorMember.status.eq(status) : null;
    }
}
