package four_tential.potential.application.course;

import four_tential.potential.common.dto.PageResponse;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.course.course.Course;
import four_tential.potential.domain.course.course.CourseLevel;
import four_tential.potential.domain.course.course.CourseListQueryResult;
import four_tential.potential.domain.course.course.CourseRepository;
import four_tential.potential.domain.course.course.CourseStatus;
import four_tential.potential.domain.course.course.CourseSearchCondition;
import four_tential.potential.domain.course.course.InstructorCourseQueryResult;
import four_tential.potential.domain.attendance.AttendanceStatus;
import four_tential.potential.domain.course.fixture.CourseCategoryFixture;
import four_tential.potential.domain.course.course_wishlist.CourseWishlistRepository;
import four_tential.potential.domain.course.fixture.CourseFixture;
import four_tential.potential.domain.member.fixture.InstructorMemberFixture;
import four_tential.potential.domain.member.instructor_member.InstructorMember;
import four_tential.potential.domain.member.instructor_member.InstructorMemberRepository;
import four_tential.potential.domain.order.CourseStudentQueryResult;
import four_tential.potential.domain.order.OrderRepository;
import four_tential.potential.presentation.course.model.response.CourseDetailInstructorInfo;
import four_tential.potential.presentation.course.model.response.CourseDetailResponse;
import four_tential.potential.presentation.course.model.response.CourseListItem;
import four_tential.potential.presentation.course.model.response.CourseStudentItem;
import four_tential.potential.presentation.course.model.response.InstructorCourseListItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
class CourseQueryServiceTest {

    @Mock private CourseRepository courseRepository;
    @Mock private CourseWishlistRepository courseWishlistRepository;
    @Mock private InstructorMemberRepository instructorMemberRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private CourseCacheQueryService courseCacheQueryService;

    @InjectMocks
    private CourseQueryService courseQueryService;

    @Test
    @DisplayName("코스 목록 조회 성공 - 인증 유저이고 위시리스트에 등록된 코스는 isWishlisted=true")
    void getCourses_authenticated_wishlistedCourse_returnsTrue() {
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        CourseListQueryResult result = sampleQueryResult(courseId);
        Pageable pageable = PageRequest.of(0, 10);

        given(courseRepository.findCourses(any(), any())).willReturn(new PageImpl<>(List.of(result), pageable, 1));
        given(courseWishlistRepository.findWishlistedCourseIds(memberId, List.of(courseId)))
                .willReturn(List.of(courseId));

        PageResponse<CourseListItem> response = courseQueryService.getCourses(emptyCondition(), memberId, pageable);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().getFirst().isWishlisted()).isTrue();
        assertThat(response.content().getFirst().courseId()).isEqualTo(courseId);
    }

    @Test
    @DisplayName("코스 목록 조회 성공 - 인증 유저이지만 위시리스트에 없는 코스는 isWishlisted=false")
    void getCourses_authenticated_notWishlisted_returnsFalse() {
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        CourseListQueryResult result = sampleQueryResult(courseId);
        Pageable pageable = PageRequest.of(0, 10);

        given(courseRepository.findCourses(any(), any())).willReturn(new PageImpl<>(List.of(result), pageable, 1));
        given(courseWishlistRepository.findWishlistedCourseIds(memberId, List.of(courseId)))
                .willReturn(List.of());

        PageResponse<CourseListItem> response = courseQueryService.getCourses(emptyCondition(), memberId, pageable);

        assertThat(response.content().get(0).isWishlisted()).isFalse();
    }

    @Test
    @DisplayName("코스 목록 조회 성공 - 비인증 유저(memberId=null)이면 isWishlisted=false이고 위시리스트 조회 안 함")
    void getCourses_notAuthenticated_isWishlistedFalse_noWishlistQuery() {
        UUID courseId = UUID.randomUUID();
        CourseListQueryResult result = sampleQueryResult(courseId);
        Pageable pageable = PageRequest.of(0, 10);

        given(courseRepository.findCourses(any(), any())).willReturn(new PageImpl<>(List.of(result), pageable, 1));

        PageResponse<CourseListItem> response = courseQueryService.getCourses(emptyCondition(), null, pageable);

        assertThat(response.content().get(0).isWishlisted()).isFalse();
        verify(courseWishlistRepository, never()).findWishlistedCourseIds(any(), any());
    }

    @Test
    @DisplayName("코스 목록 조회 성공 - 조건에 맞는 코스가 없으면 빈 페이지 반환")
    void getCourses_noResults_returnsEmptyPage() {
        Pageable pageable = PageRequest.of(0, 10);

        given(courseRepository.findCourses(any(), any())).willReturn(new PageImpl<>(List.of(), pageable, 0));

        PageResponse<CourseListItem> response = courseQueryService.getCourses(emptyCondition(), UUID.randomUUID(), pageable);

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
        verify(courseWishlistRepository, never()).findWishlistedCourseIds(any(), any());
    }

    @Test
    @DisplayName("코스 목록 조회 성공 - 응답 DTO에 level, price, status 등 주요 필드가 올바르게 매핑된다")
    void getCourses_responseMappedCorrectly() {
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        CourseListQueryResult result = sampleQueryResult(courseId);
        Pageable pageable = PageRequest.of(0, 10);

        given(courseRepository.findCourses(any(), any())).willReturn(new PageImpl<>(List.of(result), pageable, 1));
        given(courseWishlistRepository.findWishlistedCourseIds(any(), any())).willReturn(List.of());

        PageResponse<CourseListItem> response = courseQueryService.getCourses(emptyCondition(), memberId, pageable);
        CourseListItem item = response.content().get(0);

        assertThat(item.courseId()).isEqualTo(courseId);
        assertThat(item.title()).isEqualTo("테스트 강의");
        assertThat(item.level()).isEqualTo(CourseLevel.BEGINNER);
        assertThat(item.status()).isEqualTo(CourseStatus.OPEN);
        assertThat(item.price()).isEqualTo(BigInteger.valueOf(50000));
        assertThat(item.instructor().name()).isEqualTo("강사이름");
        assertThat(item.thumbnailUrl()).isEqualTo("https://cdn.example.com/thumb.jpg");
    }

    @Test
    @DisplayName("코스 상세 조회 성공 - 캐시된 코스 데이터에 isWishlisted가 올바르게 합성된다")
    void getCourseDetail_success_allFieldsMapped() {
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID instructorMemberId = UUID.randomUUID();

        given(courseCacheQueryService.getCourseDetailCache(courseId))
                .willReturn(sampleCourseDetailResponse(courseId, instructorMemberId));
        given(courseWishlistRepository.existsByMemberIdAndCourseId(memberId, courseId)).willReturn(true);

        CourseDetailResponse response = courseQueryService.getCourseDetail(courseId, memberId);

        assertThat(response.courseId()).isEqualTo(courseId);
        assertThat(response.title()).isEqualTo(CourseFixture.DEFAULT_TITLE);
        assertThat(response.isWishlisted()).isTrue();
        assertThat(response.instructor().memberId()).isEqualTo(instructorMemberId);
    }

    @Test
    @DisplayName("코스 상세 조회 성공 - 비인증 유저(memberId=null)이면 isWishlisted=false이고 위시리스트 조회 안 함")
    void getCourseDetail_notAuthenticated_isWishlistedFalse_noWishlistQuery() {
        UUID courseId = UUID.randomUUID();

        given(courseCacheQueryService.getCourseDetailCache(courseId))
                .willReturn(sampleCourseDetailResponse(courseId, UUID.randomUUID()));

        CourseDetailResponse response = courseQueryService.getCourseDetail(courseId, null);

        assertThat(response.isWishlisted()).isFalse();
        verify(courseWishlistRepository, never()).existsByMemberIdAndCourseId(any(), any());
    }

    @Test
    @DisplayName("코스 상세 조회 성공 - 코스 이미지 URL이 응답에 포함된다")
    void getCourseDetail_imagesIncludedInResponse() {
        UUID courseId = UUID.randomUUID();

        CourseDetailResponse cached = sampleCourseDetailResponse(courseId, UUID.randomUUID());
        CourseDetailResponse withImages = new CourseDetailResponse(
                cached.courseId(), cached.title(), cached.description(),
                cached.categoryCode(), cached.categoryName(), cached.instructor(),
                List.of("https://cdn.example.com/img1.jpg", "https://cdn.example.com/img2.jpg"),
                cached.addressMain(), cached.addressDetail(),
                cached.price(), cached.capacity(), cached.confirmCount(),
                cached.status(), cached.level(),
                cached.orderOpenAt(), cached.orderCloseAt(),
                cached.startAt(), cached.endAt(),
                cached.averageRating(), cached.reviewCount(), false
        );
        given(courseCacheQueryService.getCourseDetailCache(courseId)).willReturn(withImages);
        given(courseWishlistRepository.existsByMemberIdAndCourseId(any(), any())).willReturn(false);

        CourseDetailResponse response = courseQueryService.getCourseDetail(courseId, UUID.randomUUID());

        assertThat(response.images()).containsExactly(
                "https://cdn.example.com/img1.jpg",
                "https://cdn.example.com/img2.jpg"
        );
    }

    @Test
    @DisplayName("코스 상세 조회 실패 - 존재하지 않는 코스 ID이면 NOT_FOUND")
    void getCourseDetail_courseNotFound_throwsNotFound() {
        UUID courseId = UUID.randomUUID();
        given(courseCacheQueryService.getCourseDetailCache(courseId))
                .willThrow(new ServiceErrorException(four_tential.potential.common.exception.domain.CourseExceptionEnum.ERR_NOT_FOUND_COURSE));

        assertThatThrownBy(() -> courseQueryService.getCourseDetail(courseId, UUID.randomUUID()))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 코스입니다");
    }

    @Test
    @DisplayName("내 코스 목록 조회 성공 - PREPARATION 포함 전체 코스 반환")
    void getMyInstructorCourses_success_includesPreparation() {
        UUID memberId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        InstructorMember instructor = approvedInstructorMember();

        given(instructorMemberRepository.findByMemberId(memberId)).willReturn(Optional.of(instructor));
        given(courseRepository.findMyCoursesByInstructorMemberId(instructor.getId(), pageable))
                .willReturn(new PageImpl<>(List.of(
                        sampleInstructorCourseQueryResult(CourseStatus.OPEN),
                        sampleInstructorCourseQueryResult(CourseStatus.PREPARATION)
                ), pageable, 2));

        PageResponse<InstructorCourseListItem> response = courseQueryService.getMyInstructorCourses(memberId, pageable);

        assertThat(response.content()).hasSize(2);
    }

    @Test
    @DisplayName("내 코스 목록 조회 실패 - 강사 등록이 없으면 ServiceErrorException 발생")
    void getMyInstructorCourses_instructorNotFound_throwsException() {
        UUID memberId = UUID.randomUUID();
        given(instructorMemberRepository.findByMemberId(memberId)).willReturn(Optional.empty());

        assertThatThrownBy(() ->
                courseQueryService.getMyInstructorCourses(memberId, PageRequest.of(0, 10))
        )
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 강사입니다");
    }

    @Test
    @DisplayName("수강생 명단 조회 성공 - CONFIRMED 수강생 목록과 출석 정보 반환")
    void getCourseStudents_success() {
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);

        InstructorMember instructor = approvedInstructorMember();
        Course course = openCourseWithId(courseId);
        ReflectionTestUtils.setField(course, "memberInstructorId", instructor.getId());

        CourseStudentQueryResult studentResult = new CourseStudentQueryResult(
                UUID.randomUUID(), "김수강", AttendanceStatus.ATTEND,
                LocalDateTime.of(2026, 1, 20, 14, 5)
        );

        given(instructorMemberRepository.findByMemberId(memberId)).willReturn(Optional.of(instructor));
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));
        given(orderRepository.findConfirmedStudentsByCourseId(courseId, pageable))
                .willReturn(new PageImpl<>(List.of(studentResult), pageable, 1));

        PageResponse<CourseStudentItem> response = courseQueryService.getCourseStudents(courseId, memberId, pageable);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).memberName()).isEqualTo("김수강");
    }

    @Test
    @DisplayName("수강생 명단 조회 실패 - 본인 코스가 아니면 ERR_FORBIDDEN_COURSE")
    void getCourseStudents_notOwnCourse_throwsForbidden() {
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        InstructorMember instructor = approvedInstructorMember();

        Course course = courseWithId(courseId);
        ReflectionTestUtils.setField(course, "memberInstructorId", UUID.randomUUID());

        given(instructorMemberRepository.findByMemberId(memberId)).willReturn(Optional.of(instructor));
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));

        assertThatThrownBy(() ->
                courseQueryService.getCourseStudents(courseId, memberId, PageRequest.of(0, 10))
        )
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("본인 코스만 조회 가능합니다");
    }

    @Test
    @DisplayName("수강생 명단 조회 실패 - 강사 등록이 없으면 ERR_NOT_FOUND_INSTRUCTOR")
    void getCourseStudents_instructorNotFound() {
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();

        given(instructorMemberRepository.findByMemberId(memberId)).willReturn(Optional.empty());

        assertThatThrownBy(() ->
                courseQueryService.getCourseStudents(courseId, memberId, PageRequest.of(0, 10))
        )
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 강사입니다");
    }

    @Test
    @DisplayName("수강생 명단 조회 실패 - 코스가 존재하지 않으면 ERR_NOT_FOUND_COURSE")
    void getCourseStudents_courseNotFound() {
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        InstructorMember instructor = approvedInstructorMember();

        given(instructorMemberRepository.findByMemberId(memberId)).willReturn(Optional.of(instructor));
        given(courseRepository.findById(courseId)).willReturn(Optional.empty());

        assertThatThrownBy(() ->
                courseQueryService.getCourseStudents(courseId, memberId, PageRequest.of(0, 10))
        )
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 코스입니다");
    }

    @Test
    @DisplayName("수강생 명단 조회 실패 - PREPARATION 상태 코스이면 ERR_COURSE_IN_PREPARATION")
    void getCourseStudents_preparationStatus() {
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        InstructorMember instructor = approvedInstructorMember();

        Course course = courseWithId(courseId);
        ReflectionTestUtils.setField(course, "memberInstructorId", instructor.getId());

        given(instructorMemberRepository.findByMemberId(memberId)).willReturn(Optional.of(instructor));
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));

        assertThatThrownBy(() ->
                courseQueryService.getCourseStudents(courseId, memberId, PageRequest.of(0, 10))
        )
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("준비 중인 코스는 수강생을 조회할 수 없습니다");
    }

    @Test
    @DisplayName("강사 코스 목록 조회 성공 - 공개 프로필용 코스 목록 반환")
    void getInstructorCourses_success() {
        UUID instructorId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        InstructorMember instructor = approvedInstructorMember();

        given(instructorMemberRepository.findByMemberId(instructorId)).willReturn(Optional.of(instructor));
        given(courseRepository.findCoursesByInstructorMemberId(instructor.getId(), pageable))
                .willReturn(new PageImpl<>(List.of(
                        sampleInstructorCourseQueryResult(CourseStatus.OPEN)
                ), pageable, 1));

        PageResponse<InstructorCourseListItem> response = courseQueryService.getInstructorCourses(instructorId, pageable);

        assertThat(response.content()).hasSize(1);
    }

    @Test
    @DisplayName("강사 코스 목록 조회 실패 - 강사 등록이 없으면 ERR_NOT_FOUND_INSTRUCTOR")
    void getInstructorCourses_instructorNotFound() {
        UUID instructorId = UUID.randomUUID();

        given(instructorMemberRepository.findByMemberId(instructorId)).willReturn(Optional.empty());

        assertThatThrownBy(() ->
                courseQueryService.getInstructorCourses(instructorId, PageRequest.of(0, 10))
        )
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 강사입니다");
    }

    @Test
    @DisplayName("코스 엔티티 조회 성공")
    void getCourseEntity_success() {
        UUID courseId = UUID.randomUUID();
        Course course = courseWithId(courseId);

        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));

        Course result = courseQueryService.getCourseEntity(courseId);

        assertThat(result.getId()).isEqualTo(courseId);
    }

    @Test
    @DisplayName("코스 엔티티 조회 실패 - 존재하지 않으면 ERR_NOT_FOUND_COURSE")
    void getCourseEntity_notFound() {
        UUID courseId = UUID.randomUUID();

        given(courseRepository.findById(courseId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> courseQueryService.getCourseEntity(courseId))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 코스입니다");
    }

    private CourseSearchCondition emptyCondition() {
        return new CourseSearchCondition(null, null, null, null, null, null, null, null);
    }

    private CourseListQueryResult sampleQueryResult(UUID courseId) {
        return new CourseListQueryResult(
                courseId, "테스트 강의", "BACKEND", "백엔드",
                UUID.randomUUID(), "강사이름", "https://cdn.example.com/profile.jpg",
                "https://cdn.example.com/thumb.jpg", BigInteger.valueOf(50000),
                20, 5, CourseStatus.OPEN, CourseLevel.BEGINNER,
                LocalDateTime.of(2026, 1, 1, 9, 0),
                LocalDateTime.of(2026, 1, 12, 9, 0)
        );
    }

    private InstructorCourseQueryResult sampleInstructorCourseQueryResult(CourseStatus status) {
        return new InstructorCourseQueryResult(
                UUID.randomUUID(), "테스트 강의", CourseLevel.BEGINNER, status,
                20, 5, BigInteger.valueOf(50000),
                LocalDateTime.of(2026, 1, 1, 9, 0),
                LocalDateTime.of(2026, 1, 12, 9, 0)
        );
    }

    private CourseDetailResponse sampleCourseDetailResponse(UUID courseId, UUID instructorMemberId) {
        return new CourseDetailResponse(
                courseId, CourseFixture.DEFAULT_TITLE, CourseFixture.DEFAULT_DESCRIPTION,
                CourseCategoryFixture.DEFAULT_CODE, CourseCategoryFixture.DEFAULT_NAME,
                new CourseDetailInstructorInfo(
                        instructorMemberId, "강사이름",
                        "https://cdn.example.com/profile.jpg", 4.2
                ),
                List.of(), CourseFixture.DEFAULT_ADDRESS_MAIN, CourseFixture.DEFAULT_ADDRESS_DETAIL,
                CourseFixture.DEFAULT_PRICE, CourseFixture.DEFAULT_CAPACITY, 5,
                CourseStatus.OPEN, CourseFixture.DEFAULT_LEVEL,
                CourseFixture.DEFAULT_ORDER_OPEN_AT, CourseFixture.DEFAULT_ORDER_CLOSE_AT,
                CourseFixture.DEFAULT_START_AT, CourseFixture.DEFAULT_END_AT,
                4.5, 15L, false
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
