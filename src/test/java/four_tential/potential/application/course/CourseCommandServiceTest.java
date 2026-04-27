package four_tential.potential.application.course;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.course.course.Course;
import four_tential.potential.domain.course.course.CourseLevel;
import four_tential.potential.domain.course.course.CourseRepository;
import four_tential.potential.domain.course.course.CourseStatus;
import four_tential.potential.domain.course.course_category.CourseCategory;
import four_tential.potential.domain.course.course_category.CourseCategoryRepository;
import four_tential.potential.domain.course.course_image.CourseImageRepository;
import four_tential.potential.domain.course.course_wishlist.CourseWishlistRepository;
import four_tential.potential.domain.course.fixture.CourseCategoryFixture;
import four_tential.potential.domain.course.fixture.CourseFixture;
import four_tential.potential.domain.member.fixture.InstructorMemberFixture;
import four_tential.potential.domain.member.instructor_member.InstructorMember;
import four_tential.potential.domain.member.instructor_member.InstructorMemberRepository;
import four_tential.potential.presentation.course.model.request.CreateCourseRequestRequest;
import four_tential.potential.presentation.course.model.request.UpdateCourseRequest;
import four_tential.potential.presentation.course.model.response.CreateCourseRequestResponse;
import four_tential.potential.presentation.course.model.response.UpdateCourseResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CourseCommandServiceTest {

    @Mock private CourseRepository courseRepository;
    @Mock private CourseImageRepository courseImageRepository;
    @Mock private CourseCategoryRepository courseCategoryRepository;
    @Mock private CourseWishlistRepository courseWishlistRepository;
    @Mock private InstructorMemberRepository instructorMemberRepository;

    @InjectMocks
    private CourseCommandService courseCommandService;

    @Test
    @DisplayName("코스 개설 신청 성공 - 강사의 카테고리로 PREPARATION 코스 생성")
    void createCourseRequest_success() {
        UUID memberId = UUID.randomUUID();
        InstructorMember instructor = approvedInstructorMember();
        CourseCategory category = CourseCategoryFixture.defaultCourseCategory();
        CreateCourseRequestRequest request = defaultCreateRequest(List.of("https://cdn.example.com/img1.jpg"));

        given(instructorMemberRepository.findByMemberId(memberId)).willReturn(Optional.of(instructor));
        given(courseCategoryRepository.findByCode(instructor.getCategoryCode())).willReturn(Optional.of(category));
        given(courseRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        given(courseImageRepository.saveAll(any())).willAnswer(invocation -> invocation.getArgument(0));

        CreateCourseRequestResponse response = courseCommandService.createCourseRequest(memberId, request);

        assertThat(response.title()).isEqualTo(request.title());
        assertThat(response.status()).isEqualTo(CourseStatus.PREPARATION);
        verify(courseRepository).save(any(Course.class));
    }

    @Test
    @DisplayName("코스 개설 신청 실패 - 미승인 강사이면 ERR_NOT_FOUND_INSTRUCTOR")
    void createCourseRequest_notApprovedInstructor() {
        UUID memberId = UUID.randomUUID();
        InstructorMember pending = InstructorMemberFixture.defaultInstructorMember();
        given(instructorMemberRepository.findByMemberId(memberId)).willReturn(Optional.of(pending));

        assertThatThrownBy(() -> courseCommandService.createCourseRequest(memberId, defaultCreateRequest(null)))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 강사입니다");
    }

    @Test
    @DisplayName("코스 개설 신청 취소 성공 - PREPARATION 상태 코스 삭제")
    void deleteCourseRequest_success() {
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        InstructorMember instructor = approvedInstructorMember();
        Course course = courseWithId(courseId);
        ReflectionTestUtils.setField(course, "memberInstructorId", instructor.getId());

        given(instructorMemberRepository.findByMemberId(memberId)).willReturn(Optional.of(instructor));
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));

        courseCommandService.deleteCourseRequest(memberId, courseId);

        verify(courseRepository).delete(course);
    }

    @Test
    @DisplayName("코스 개설 신청 취소 실패 - PREPARATION이 아닌 코스는 ERR_CANNOT_DELETE_COURSE_REQUEST")
    void deleteCourseRequest_notPreparation() {
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        InstructorMember instructor = approvedInstructorMember();
        Course course = openCourseWithId(courseId);
        ReflectionTestUtils.setField(course, "memberInstructorId", instructor.getId());

        given(instructorMemberRepository.findByMemberId(memberId)).willReturn(Optional.of(instructor));
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));

        assertThatThrownBy(() -> courseCommandService.deleteCourseRequest(memberId, courseId))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("PREPARATION 상태의 코스만 삭제할 수 있습니다");

        verify(courseRepository, never()).delete(any());
    }

    @Test
    @DisplayName("코스 수정 성공 - PREPARATION 코스의 모든 필드가 수정된다")
    void updateCourse_preparation_success() {
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        InstructorMember instructor = approvedInstructorMember();
        Course course = courseWithId(courseId);
        ReflectionTestUtils.setField(course, "memberInstructorId", instructor.getId());

        given(instructorMemberRepository.findByMemberId(memberId)).willReturn(Optional.of(instructor));
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));
        given(courseImageRepository.saveAll(any())).willAnswer(invocation -> invocation.getArgument(0));

        UpdateCourseResponse response = courseCommandService.updateCourse(memberId, courseId, defaultUpdateRequest());

        assertThat(response.courseId()).isEqualTo(courseId);
        assertThat(response.title()).isEqualTo("수정된 제목");
    }

    @Test
    @DisplayName("코스 수정 실패 - OPEN 코스에서 수정 불가 필드 포함 시 ERR_IMMUTABLE_FIELD_IN_OPEN")
    void updateCourse_open_withPrepOnlyFields_throwsConflict() {
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        InstructorMember instructor = approvedInstructorMember();
        Course course = openCourseWithId(courseId);
        ReflectionTestUtils.setField(course, "memberInstructorId", instructor.getId());

        given(instructorMemberRepository.findByMemberId(memberId)).willReturn(Optional.of(instructor));
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));

        assertThatThrownBy(() -> courseCommandService.updateCourse(memberId, courseId, defaultUpdateRequest()))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("OPEN 상태에서는 가격, 일정, 장소, 정원을 수정할 수 없습니다");
    }

    @Test
    @DisplayName("코스 종료 성공 - OPEN 코스가 CLOSED로 전이되고 찜 목록이 삭제된다")
    void closeCourse_success() {
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        InstructorMember instructor = approvedInstructorMember();
        Course course = openCourseWithId(courseId);
        ReflectionTestUtils.setField(course, "memberInstructorId", instructor.getId());

        given(instructorMemberRepository.findByMemberId(memberId)).willReturn(Optional.of(instructor));
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));

        courseCommandService.closeCourse(memberId, courseId);

        assertThat(course.getStatus()).isEqualTo(CourseStatus.CLOSED);
        verify(courseWishlistRepository).deleteByCourseId(courseId);
    }

    @Test
    @DisplayName("코스 종료 실패 - OPEN이 아닌 코스(PREPARATION)이면 예외")
    void closeCourse_notOpenCourse() {
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        InstructorMember instructor = approvedInstructorMember();
        Course course = courseWithId(courseId);
        ReflectionTestUtils.setField(course, "memberInstructorId", instructor.getId());

        given(instructorMemberRepository.findByMemberId(memberId)).willReturn(Optional.of(instructor));
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));

        assertThatThrownBy(() -> courseCommandService.closeCourse(memberId, courseId))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("OPEN 상태의 코스만 CLOSE 할 수 있습니다");
    }

    @Test
    @DisplayName("코스 개설 재신청 성공 - REJECTED 코스가 PREPARATION으로 전이")
    void reapplyCourseRequest_success() {
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        InstructorMember instructor = approvedInstructorMember();
        Course course = courseWithId(courseId);
        course.reject("사진 자료 미비");
        ReflectionTestUtils.setField(course, "memberInstructorId", instructor.getId());

        given(instructorMemberRepository.findByMemberId(memberId)).willReturn(Optional.of(instructor));
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));

        courseCommandService.reapplyCourseRequest(memberId, courseId);

        assertThat(course.getStatus()).isEqualTo(CourseStatus.PREPARATION);
    }

    private UpdateCourseRequest defaultUpdateRequest() {
        return new UpdateCourseRequest(
                "수정된 제목", "수정된 설명입니다.",
                CourseLevel.INTERMEDIATE,
                "서울시 서초구 강남대로 456", "2층 필라테스 스튜디오",
                BigInteger.valueOf(65000), 12,
                LocalDateTime.now().plusDays(10),
                LocalDateTime.now().plusDays(20),
                LocalDateTime.now().plusDays(30),
                LocalDateTime.now().plusDays(30).plusHours(2),
                List.of("https://cdn.example.com/img1.jpg")
        );
    }

    private CreateCourseRequestRequest defaultCreateRequest(List<String> imageUrls) {
        return new CreateCourseRequestRequest(
                "소도구 필라테스 입문반", "소도구를 활용한 전신 필라테스 수업입니다.",
                "서울시 강남구 테헤란로 123", "3층 필라테스룸",
                BigInteger.valueOf(70000), 10,
                LocalDateTime.now().plusDays(10),
                LocalDateTime.now().plusDays(20),
                LocalDateTime.now().plusDays(30),
                LocalDateTime.now().plusDays(30).plusHours(2),
                CourseLevel.BEGINNER, imageUrls
        );
    }

    private Course courseWithId(UUID courseId) {
        Course course = CourseFixture.defaultCourse();
        ReflectionTestUtils.setField(course, "id", courseId);
        return course;
    }

    private Course openCourseWithId(UUID courseId) {
        Course course = CourseFixture.defaultCourse();
        course.open();
        ReflectionTestUtils.setField(course, "id", courseId);
        return course;
    }

    private InstructorMember approvedInstructorMember() {
        InstructorMember im = InstructorMemberFixture.defaultInstructorMember();
        im.approve();
        ReflectionTestUtils.setField(im, "id", UUID.randomUUID());
        return im;
    }
}
