package four_tential.potential.domain.member.follow;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import static four_tential.potential.domain.course.course.QCourse.course;
import static four_tential.potential.domain.course.course_category.QCourseCategory.courseCategory;
import static four_tential.potential.domain.member.follow.QFollow.follow;
import static four_tential.potential.domain.member.instructor_member.QInstructorMember.instructorMember;
import static four_tential.potential.domain.member.member.QMember.member;
import static four_tential.potential.domain.review.review.QReview.review;

@RequiredArgsConstructor
public class FollowQueryRepositoryImpl implements FollowQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<FollowQueryResult> findFollowedInstructors(UUID followerId, Pageable pageable) {
        List<FollowQueryResult> content = queryFactory
                .select(Projections.constructor(FollowQueryResult.class,
                        member.id,
                        member.name,
                        member.profileImageUrl,
                        instructorMember.categoryCode,
                        courseCategory.name,
                        JPAExpressions.select(course.count())
                                .from(course)
                                .where(course.memberInstructorId.eq(instructorMember.id)),
                        Expressions.asNumber(
                                JPAExpressions.select(review.rating.avg().coalesce(0.0))
                                        .from(review)
                                        .join(course).on(course.id.eq(review.courseId))
                                        .where(course.memberInstructorId.eq(instructorMember.id))
                        ).doubleValue(),
                        follow.createdAt
                ))
                .from(follow)
                .join(instructorMember).on(instructorMember.id.eq(follow.memberInstructorId))
                .join(member).on(member.id.eq(instructorMember.memberId))
                .join(courseCategory).on(courseCategory.code.eq(instructorMember.categoryCode))
                .where(follow.memberId.eq(followerId))
                .orderBy(follow.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(follow.id.count())
                .from(follow)
                .where(follow.memberId.eq(followerId))
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0);
    }
}
