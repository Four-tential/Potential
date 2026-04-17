package four_tential.potential.domain.member.instructor_member;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import four_tential.potential.domain.course.course_category.QCourseCategory;
import four_tential.potential.domain.member.member.QMember;
import four_tential.potential.presentation.instructor_member.model.response.InstructorApplicationDetail;
import four_tential.potential.presentation.instructor_member.model.response.InstructorApplicationItem;
import four_tential.potential.presentation.instructor_member.model.response.MyInstructorApplicationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static four_tential.potential.domain.course.course_category.QCourseCategory.courseCategory;
import static four_tential.potential.domain.member.instructor_member.QInstructorMember.instructorMember;
import static four_tential.potential.domain.member.member.QMember.member;

@RequiredArgsConstructor
public class InstructorMemberQueryRepositoryImpl implements InstructorMemberQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<InstructorApplicationItem> findInstructorApplications(InstructorMemberStatus status, Pageable pageable) {
        List<InstructorApplicationItem> content = queryFactory
                .select(Projections.constructor(InstructorApplicationItem.class,
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
                .join(member).on(member.id.eq(instructorMember.memberId))
                .join(courseCategory).on(courseCategory.code.eq(instructorMember.categoryCode))
                .where(statusEq(status))
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0);
    }

    @Override
    public Optional<InstructorApplicationDetail> findInstructorApplicationDetail(UUID memberId) {
        return Optional.ofNullable(
                queryFactory
                        .select(Projections.constructor(InstructorApplicationDetail.class,
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
    public Optional<MyInstructorApplicationResponse> findMyInstructorApplication(UUID memberId) {
        return Optional.ofNullable(
                queryFactory
                        .select(Projections.constructor(MyInstructorApplicationResponse.class,
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

    private BooleanExpression statusEq(InstructorMemberStatus status) {
        return status != null ? instructorMember.status.eq(status) : null;
    }
}
