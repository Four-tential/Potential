package four_tential.potential.application.course;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.course.course.Course;
import four_tential.potential.domain.course.course.CourseRepository;
import four_tential.potential.domain.course.course.CourseStatus;
import four_tential.potential.domain.course.course_approval_history.CourseApprovalAction;
import four_tential.potential.domain.course.course_approval_history.CourseApprovalHistory;
import four_tential.potential.domain.course.course_approval_history.CourseApprovalHistoryRepository;
import four_tential.potential.domain.course.fixture.CourseFixture;
import four_tential.potential.presentation.course.model.request.CourseRequestActionRequest;
import four_tential.potential.presentation.course.model.response.CourseRequestActionResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CourseApprovalServiceTest {

    @Mock private CourseRepository courseRepository;
    @Mock private CourseApprovalHistoryRepository courseApprovalHistoryRepository;

    @InjectMocks
    private CourseApprovalService courseApprovalService;

    @Test
    @DisplayName("코스 개설 신청 승인 성공 - PREPARATION 코스가 OPEN으로 전이되고 이력 저장")
    void courseRequest_approve_success() {
        UUID courseId = UUID.randomUUID();
        Course course = courseWithId(courseId);

        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));

        CourseRequestActionResponse response =
                courseApprovalService.courseRequest(courseId, new CourseRequestActionRequest(CourseApprovalAction.APPROVE, null));

        assertThat(response.courseId()).isEqualTo(courseId);
        assertThat(response.status()).isEqualTo(CourseStatus.OPEN);

        ArgumentCaptor<CourseApprovalHistory> captor = ArgumentCaptor.forClass(CourseApprovalHistory.class);
        verify(courseApprovalHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo(CourseApprovalAction.APPROVE);
        assertThat(captor.getValue().getRejectReason()).isNull();
    }

    @Test
    @DisplayName("코스 개설 신청 반려 성공 - REJECTED 상태로 전이, 반려 이력 저장")
    void courseRequest_reject_success() {
        UUID courseId = UUID.randomUUID();
        Course course = courseWithId(courseId);

        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));

        CourseRequestActionResponse response =
                courseApprovalService.courseRequest(courseId, new CourseRequestActionRequest(CourseApprovalAction.REJECT, "사진 자료 미비"));

        assertThat(response.status()).isEqualTo(CourseStatus.REJECTED);

        ArgumentCaptor<CourseApprovalHistory> captor = ArgumentCaptor.forClass(CourseApprovalHistory.class);
        verify(courseApprovalHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getRejectReason()).isEqualTo("사진 자료 미비");
    }

    @Test
    @DisplayName("코스 개설 신청 반려 실패 - rejectReason 없으면 ERR_REJECT_REASON_REQUIRED")
    void courseRequest_rejectWithoutReason() {
        UUID courseId = UUID.randomUUID();
        Course course = courseWithId(courseId);

        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));

        assertThatThrownBy(() ->
                courseApprovalService.courseRequest(courseId, new CourseRequestActionRequest(CourseApprovalAction.REJECT, null))
        )
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("반려 사유는 필수입니다");
    }

    @Test
    @DisplayName("코스 개설 신청 승인/반려 실패 - PREPARATION이 아니면 ERR_COURSE_NOT_IN_PREPARATION")
    void courseRequest_notPreparation() {
        UUID courseId = UUID.randomUUID();
        Course course = CourseFixture.defaultCourse();
        course.open();
        ReflectionTestUtils.setField(course, "id", courseId);

        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));

        assertThatThrownBy(() ->
                courseApprovalService.courseRequest(courseId, new CourseRequestActionRequest(CourseApprovalAction.APPROVE, null))
        )
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("PREPARATION 상태의 코스만 승인 또는 반려할 수 있습니다");
    }

    private Course courseWithId(UUID courseId) {
        Course course = CourseFixture.defaultCourse();
        ReflectionTestUtils.setField(course, "id", courseId);
        return course;
    }
}
