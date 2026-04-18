package four_tential.potential.domain.member.follow;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import four_tential.potential.presentation.member.model.response.FollowedInstructorItem;
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
    public Page<FollowedInstructorItem> findFollowedInstructors(UUID followerId, Pageable pageable) {
        List<FollowedInstructorItem> content = queryFactory
                .select(Projections.constructor(FollowedInstructorItem.class,
                        member.id,
                        member.name,
                        member.profileImageUrl,
                        instructorMember.categoryCode,
                        courseCategory.name,
                        course.id.countDistinct(),
                        review.rating.avg(),
                        follow.createdAt
                ))
                .from(follow)
                .join(instructorMember).on(instructorMember.id.eq(follow.memberInstructorId))
                .join(member).on(member.id.eq(instructorMember.memberId))
                .join(courseCategory).on(courseCategory.code.eq(instructorMember.categoryCode))
                .leftJoin(course).on(course.memberInstructorId.eq(instructorMember.id))
                .leftJoin(review).on(review.courseId.eq(course.id))
                .where(follow.memberId.eq(followerId))
                .groupBy(follow.id, instructorMember.id, member.id, courseCategory.code)
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
