package four_tential.potential.domain.review.review;

import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

import static four_tential.potential.domain.course.course.QCourse.course;
import static four_tential.potential.domain.review.review.QReview.review;

@RequiredArgsConstructor
public class ReviewRepositoryImpl implements ReviewRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Double findAverageRatingByMemberInstructorId(UUID memberInstructorId) {
        return queryFactory
                .select(review.rating.avg())
                .from(review)
                .join(course).on(course.id.eq(review.courseId))
                .where(course.memberInstructorId.eq(memberInstructorId))
                .fetchOne();
    }
}
