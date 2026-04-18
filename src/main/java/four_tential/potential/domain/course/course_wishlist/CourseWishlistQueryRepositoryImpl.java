package four_tential.potential.domain.course.course_wishlist;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import four_tential.potential.domain.course.course_image.QCourseImage;
import four_tential.potential.presentation.member.model.response.WishlistCourseItem;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import static four_tential.potential.domain.course.course.QCourse.course;
import static four_tential.potential.domain.course.course_category.QCourseCategory.courseCategory;
import static four_tential.potential.domain.course.course_image.QCourseImage.courseImage;
import static four_tential.potential.domain.course.course_wishlist.QCourseWishlist.courseWishlist;
import static four_tential.potential.domain.member.instructor_member.QInstructorMember.instructorMember;
import static four_tential.potential.domain.member.member.QMember.member;

@RequiredArgsConstructor
public class CourseWishlistQueryRepositoryImpl implements CourseWishlistQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<WishlistCourseItem> findWishlistCourses(UUID memberId, Pageable pageable) {
        // UUID v7 은 시간 정렬(time-ordered) - id.min() = 가장 먼저 등록된 이미지
        QCourseImage courseImageSub = new QCourseImage("courseImageSub"); // 서브쿼리용

        List<WishlistCourseItem> content = queryFactory
                .select(Projections.constructor(WishlistCourseItem.class,
                        course.id,
                        course.title,
                        member.name,
                        courseImage.imageUrl,   // ON 조건으로 1행 고정 - 집계 불필요
                        courseCategory.code,
                        courseCategory.name,
                        course.price,
                        course.status,
                        course.startAt,
                        courseWishlist.createdAt
                ))
                .from(courseWishlist)
                .join(course).on(course.id.eq(courseWishlist.courseId))
                .join(instructorMember).on(instructorMember.id.eq(course.memberInstructorId))
                .join(member).on(member.id.eq(instructorMember.memberId))
                .join(courseCategory).on(courseCategory.id.eq(course.courseCategoryId))
                .leftJoin(course.images, courseImage)
                        .on(courseImage.id.eq(
                                JPAExpressions.select(courseImageSub.id.min())
                                        .from(courseImageSub)
                                        .where(courseImageSub.course.id.eq(course.id))
                        ))
                .where(courseWishlist.memberId.eq(memberId))
                .orderBy(courseWishlist.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(courseWishlist.id.count())
                .from(courseWishlist)
                .where(courseWishlist.memberId.eq(memberId))
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0);
    }
}
