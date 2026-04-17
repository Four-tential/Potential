package four_tential.potential.application.attendance;

import four_tential.potential.domain.attendance.Attendance;
import four_tential.potential.domain.attendance.AttendanceRepository;
import four_tential.potential.presentation.attendance.dto.AttendanceListResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceQueryServiceTest {

    @Mock
    private AttendanceRepository attendanceRepository;

    @InjectMocks
    private AttendanceQueryService attendanceQueryService;

    private static final UUID COURSE_ID = UUID.randomUUID();
    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final UUID ORDER_ID  = UUID.randomUUID();

    @Nested
    @DisplayName("getAttendanceSnapshot() - 스냅샷 조회")
    class GetAttendanceSnapshotTest {

        @Test
        @DisplayName("현재 출석 현황 스냅샷을 반환한다")
        void getAttendanceSnapshot_success() {
            // given
            Attendance a1 = Attendance.register(ORDER_ID, MEMBER_ID, COURSE_ID);
            Attendance a2 = Attendance.register(ORDER_ID, UUID.randomUUID(), COURSE_ID);
            a1.attend("token");
            when(attendanceRepository.findStatsByCourseId(COURSE_ID))
                    .thenReturn(AttendanceListResponse.ofInstructor(List.of(a1, a2)));
            // when
            AttendanceListResponse snapshot = attendanceQueryService.getAttendanceSnapshot(COURSE_ID);

            // then
            assertThat(snapshot.getTotalCount()).isEqualTo(2);
            assertThat(snapshot.getAttendCount()).isEqualTo(1);
            assertThat(snapshot.getAbsentCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("수강생이 없으면 모든 집계가 0 인 스냅샷을 반환한다")
        void getAttendanceSnapshot_empty() {
            // given
            when(attendanceRepository.findStatsByCourseId(COURSE_ID))
                    .thenReturn(AttendanceListResponse.ofInstructor(List.of()));

            // when
            AttendanceListResponse snapshot = attendanceQueryService.getAttendanceSnapshot(COURSE_ID);

            // then
            assertThat(snapshot.getTotalCount()).isZero();
            assertThat(snapshot.getAttendCount()).isZero();
            assertThat(snapshot.getAbsentCount()).isZero();
        }
    }
}