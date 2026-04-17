package four_tential.potential.domain.attendance;

import com.querydsl.jpa.impl.JPAQueryFactory;
import four_tential.potential.presentation.attendance.dto.AttendanceListResponse;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static four_tential.potential.domain.attendance.QAttendance.attendance;

@RequiredArgsConstructor
public class AttendanceRepositoryImpl implements AttendanceRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public AttendanceListResponse findStatsByCourseId(UUID courseId) {

        // 출석 상세 목록 조회
        List<Attendance> attendances = queryFactory
                .selectFrom(attendance)
                .where(attendance.courseId.eq(courseId))
                .orderBy(attendance.attendanceAt.asc().nullsLast())
                .fetch();

        // 집계는 DB에서 처리
        Long attendCount = queryFactory
                .select(attendance.count())
                .from(attendance)
                .where(
                        attendance.courseId.eq(courseId),
                        attendance.status.eq(AttendanceStatus.ATTEND)
                )
                .fetchOne();

        int total = attendances.size();
        int attend = attendCount == null ? 0 : attendCount.intValue();
        int absent = total - attend;

        List<AttendanceListResponse.AttendanceDetail> details = attendances.stream()
                .map(a -> AttendanceListResponse.AttendanceDetail.builder()
                        .attendanceId(a.getId())
                        .memberId(a.getMemberId())
                        .status(a.getStatus())
                        .attendanceAt(a.getAttendanceAt())
                        .build())
                .toList();

        return AttendanceListResponse.builder()
                .totalCount(total)
                .attendCount(attend)
                .absentCount(absent)
                .attendances(details)
                .build();
    }

    // 수강생 본인 출석 단건 조회
    @Override
    public Optional<Attendance> findByMemberIdAndCourseIdQuery(UUID memberId, UUID courseId) {
        return Optional.ofNullable(
                queryFactory
                        .selectFrom(attendance)
                        .where(
                                attendance.memberId.eq(memberId),
                                attendance.courseId.eq(courseId)
                        )
                        .fetchOne()
        );
    }

    // 중복 출석 확인
    @Override
    public boolean existsAttendByMemberIdAndCourseId(UUID memberId, UUID courseId) {
        return queryFactory
                .selectOne()
                .from(attendance)
                .where(
                        attendance.memberId.eq(memberId),
                        attendance.courseId.eq(courseId),
                        attendance.status.eq(AttendanceStatus.ATTEND)
                )
                .fetchFirst() != null;
    }
}