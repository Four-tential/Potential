package four_tential.potential.domain.course.course;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import four_tential.potential.domain.course.course_image.QCourseImage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static four_tential.potential.domain.course.course.QCourse.course;
import static four_tential.potential.domain.course.course_category.QCourseCategory.courseCategory;
import static four_tential.potential.domain.course.course_image.QCourseImage.courseImage;
import static four_tential.potential.domain.member.instructor_member.QInstructorMember.instructorMember;
import static four_tential.potential.domain.member.member.QMember.member;
import static four_tential.potential.domain.review.review.QReview.review;

@RequiredArgsConstructor
public class CourseQueryRepositoryImpl implements CourseQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<CourseListQueryResult> findCourses(CourseSearchCondition condition, Pageable pageable) {
        QCourseImage courseImageSub = new QCourseImage("courseImageSub");

        BooleanBuilder whereConditions = buildWhereConditions(condition);

        boolean useCursor = condition.cursorId() != null && isLatestSort(condition.sort());
        if (useCursor) {
            whereConditions.and(course.id.lt(condition.cursorId()));
        }

        List<CourseListQueryResult> content = queryFactory
                .select(Projections.constructor(CourseListQueryResult.class,
                        course.id,
                        course.title,
                        courseCategory.code,
                        courseCategory.name,
                        member.id,
                        member.name,
                        member.profileImageUrl,
                        courseImage.imageUrl,
                        course.price,
                        course.capacity,
                        course.confirmCount,
                        course.status,
                        course.level,
                        course.orderOpenAt,
                        course.startAt
                ))
                .from(course)
                .join(instructorMember).on(instructorMember.id.eq(course.memberInstructorId))
                .join(member).on(member.id.eq(instructorMember.memberId))
                .leftJoin(courseCategory).on(courseCategory.id.eq(course.courseCategoryId))
                .leftJoin(course.images, courseImage)
                        .on(courseImage.id.eq(
                                JPAExpressions.select(courseImageSub.id.min())
                                        .from(courseImageSub)
                                        .where(courseImageSub.course.id.eq(course.id))
                        ))
                .where(whereConditions)
                .orderBy(buildOrderSpecifier(condition.sort()))
                .offset(useCursor ? 0 : pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(course.count())
                .from(course)
                .join(instructorMember).on(instructorMember.id.eq(course.memberInstructorId))
                .join(member).on(member.id.eq(instructorMember.memberId))
                .leftJoin(courseCategory).on(courseCategory.id.eq(course.courseCategoryId))
                .where(buildWhereConditions(condition));

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    private boolean isLatestSort(CourseSort sort) {
        return sort == null || sort == CourseSort.LATEST;
    }

    @Override
    public Optional<CourseDetailQueryResult> findCourseDetail(UUID courseId) {
        return Optional.ofNullable(
                queryFactory
                        .select(Projections.constructor(CourseDetailQueryResult.class,
                                course.id,
                                course.title,
                                course.description,
                                courseCategory.code,
                                courseCategory.name,
                                member.id,
                                member.name,
                                member.profileImageUrl,
                                course.addressMain,
                                course.addressDetail,
                                course.price,
                                course.capacity,
                                course.confirmCount,
                                course.status,
                                course.level,
                                course.orderOpenAt,
                                course.orderCloseAt,
                                course.startAt,
                                course.endAt,
                                Expressions.asNumber(
                                        JPAExpressions.select(review.rating.avg().coalesce(0.0))
                                                .from(review)
                                                .where(review.courseId.in(
                                                        JPAExpressions.select(course.id)
                                                                .from(course)
                                                                .where(course.memberInstructorId.eq(instructorMember.id))
                                                ))
                                ).doubleValue(),
                                Expressions.asNumber(
                                        JPAExpressions.select(review.rating.avg().coalesce(0.0))
                                                .from(review)
                                                .where(review.courseId.eq(courseId))
                                ).doubleValue(),
                                JPAExpressions.select(review.count())
                                        .from(review)
                                        .where(review.courseId.eq(courseId))
                        ))
                        .from(course)
                        .join(instructorMember).on(instructorMember.id.eq(course.memberInstructorId))
                        .join(member).on(member.id.eq(instructorMember.memberId))
                        .leftJoin(courseCategory).on(courseCategory.id.eq(course.courseCategoryId))
                        .where(
                                course.id.eq(courseId),
                                course.status.ne(CourseStatus.PREPARATION)
                        )
                        .fetchOne()
        );
    }

    @Override
    public Page<InstructorCourseQueryResult> findCoursesByInstructorMemberId(UUID instructorMemberId, Pageable pageable) {
        return findInstructorCourses(instructorMemberId, pageable, true);
    }

    @Override
    public Page<InstructorCourseQueryResult> findMyCoursesByInstructorMemberId(UUID instructorMemberId, Pageable pageable) {
        // 본인 조회는 PREPARATION(개설 승인 대기) 포함 전체 상태
        return findInstructorCourses(instructorMemberId, pageable, false);
    }

    private Page<InstructorCourseQueryResult> findInstructorCourses(
            UUID instructorMemberId, Pageable pageable, boolean excludePreparation
    ) {
        BooleanBuilder where = new BooleanBuilder();
        where.and(course.memberInstructorId.eq(instructorMemberId));
        if (excludePreparation) {
            where.and(course.status.ne(CourseStatus.PREPARATION));
        }

        List<InstructorCourseQueryResult> content = queryFactory
                .select(Projections.constructor(InstructorCourseQueryResult.class,
                        course.id,
                        course.title,
                        course.level,
                        course.status,
                        course.capacity,
                        course.confirmCount,
                        course.price,
                        course.orderOpenAt,
                        course.startAt
                ))
                .from(course)
                .where(where)
                .orderBy(course.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(course.count())
                .from(course)
                .where(where);

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    private BooleanBuilder buildWhereConditions(CourseSearchCondition condition) {
        BooleanBuilder builder = new BooleanBuilder();

        // PREPARATION(개설 승인 중) 코스는 공개 목록에서 항상 제외
        builder.and(course.status.ne(CourseStatus.PREPARATION));

        if (condition.categoryCode() != null && !condition.categoryCode().isBlank()) {
            builder.and(courseCategory.code.eq(condition.categoryCode()));
        }
        if (condition.status() != null) {
            builder.and(course.status.eq(condition.status()));
        }
        if (condition.level() != null) {
            builder.and(course.level.eq(condition.level()));
        }
        if (condition.keyword() != null && !condition.keyword().isBlank()) {
            builder.and(course.title.containsIgnoreCase(condition.keyword()));
        }
        if (condition.minPrice() != null) {
            builder.and(course.price.goe(condition.minPrice()));
        }
        if (condition.maxPrice() != null) {
            builder.and(course.price.loe(condition.maxPrice()));
        }

        return builder;
    }

    private OrderSpecifier<?> buildOrderSpecifier(CourseSort sort) {
        if (sort == CourseSort.PRICE_ASC) {
            return course.price.asc();
        }
        if (sort == CourseSort.PRICE_DESC) {
            return course.price.desc();
        }
        // 최신 등록순 (LATEST)
        return course.createdAt.desc();
    }
}
