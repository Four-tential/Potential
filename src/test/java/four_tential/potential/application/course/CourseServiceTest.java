package four_tential.potential.application.course;

import four_tential.potential.common.dto.PageResponse;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.course.course.Course;
import four_tential.potential.domain.course.course.CourseLevel;
import four_tential.potential.domain.course.course.CourseListQueryResult;
import four_tential.potential.domain.course.course.CourseRepository;
import four_tential.potential.domain.course.course.CourseStatus;
import four_tential.potential.domain.course.course_category.CourseCategory;
import four_tential.potential.domain.course.course_category.CourseCategoryRepository;
import four_tential.potential.domain.course.course_image.CourseImage;
import four_tential.potential.domain.attendance.AttendanceStatus;
import four_tential.potential.domain.course.course_image.CourseImageRepository;
import four_tential.potential.domain.course.course_approval_history.CourseApprovalHistoryRepository;
import four_tential.potential.domain.course.course_wishlist.CourseWishlistRepository;
import four_tential.potential.domain.course.fixture.CourseCategoryFixture;
import four_tential.potential.domain.course.fixture.CourseFixture;
import four_tential.potential.domain.member.fixture.InstructorMemberFixture;
import four_tential.potential.domain.member.fixture.MemberFixture;
import four_tential.potential.domain.member.instructor_member.InstructorMember;
import four_tential.potential.domain.member.instructor_member.InstructorMemberRepository;
import four_tential.potential.domain.member.member.Member;
import four_tential.potential.domain.member.member.MemberRepository;
import four_tential.potential.domain.order.CourseStudentQueryResult;
import four_tential.potential.domain.order.OrderRepository;
import four_tential.potential.domain.review.review.ReviewRepository;
import four_tential.potential.domain.course.course.CourseSearchCondition;
import four_tential.potential.domain.course.course.InstructorCourseQueryResult;
import four_tential.potential.domain.member.instructor_member.InstructorMemberStatus;
import four_tential.potential.domain.course.course_approval_history.CourseApprovalAction;
import four_tential.potential.domain.course.course_approval_history.CourseApprovalHistory;
import four_tential.potential.presentation.course.model.request.CourseRequestActionRequest;
import four_tential.potential.presentation.course.model.request.CreateCourseRequestRequest;
import four_tential.potential.presentation.course.model.request.UpdateCourseRequest;
import four_tential.potential.presentation.course.model.response.CourseWishlistResponse;
import four_tential.potential.presentation.course.model.response.UpdateCourseResponse;
import four_tential.potential.presentation.course.model.response.CourseDetailResponse;
import four_tential.potential.presentation.course.model.response.CourseRequestActionResponse;
import four_tential.potential.presentation.course.model.response.CourseListItem;
import four_tential.potential.presentation.course.model.response.CourseStudentItem;
import four_tential.potential.presentation.course.model.response.CreateCourseRequestResponse;
import four_tential.potential.presentation.course.model.response.InstructorCourseListItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class CourseServiceTest {

    @Mock private CourseRepository courseRepository;
    @Mock private CourseImageRepository courseImageRepository;
    @Mock private CourseApprovalHistoryRepository courseApprovalHistoryRepository;
    @Mock private CourseCategoryRepository courseCategoryRepository;
    @Mock private CourseWishlistRepository courseWishlistRepository;
    @Mock private InstructorMemberRepository instructorMemberRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private ReviewRepository reviewRepository;
    @Mock private OrderRepository orderRepository;

    @InjectMocks
    private CourseService courseService;

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

        PageResponse<CourseListItem> response = courseService.getCourses(emptyCondition(), memberId, pageable);

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

        PageResponse<CourseListItem> response = courseService.getCourses(emptyCondition(), memberId, pageable);

        assertThat(response.content().get(0).isWishlisted()).isFalse();
    }

    @Test
    @DisplayName("코스 목록 조회 성공 - 비인증 유저(memberId=null)이면 isWishlisted=false이고 위시리스트 조회 안 함")
    void getCourses_notAuthenticated_isWishlistedFalse_noWishlistQuery() {
        UUID courseId = UUID.randomUUID();
        CourseListQueryResult result = sampleQueryResult(courseId);
        Pageable pageable = PageRequest.of(0, 10);

        given(courseRepository.findCourses(any(), any())).willReturn(new PageImpl<>(List.of(result), pageable, 1));

        PageResponse<CourseListItem> response = courseService.getCourses(emptyCondition(), null, pageable);

        assertThat(response.content().get(0).isWishlisted()).isFalse();
        verify(courseWishlistRepository, never()).findWishlistedCourseIds(any(), any());
    }

    @Test
    @DisplayName("코스 목록 조회 성공 - 조건에 맞는 코스가 없으면 빈 페이지 반환")
    void getCourses_noResults_returnsEmptyPage() {
        Pageable pageable = PageRequest.of(0, 10);

        given(courseRepository.findCourses(any(), any())).willReturn(new PageImpl<>(List.of(), pageable, 0));

        PageResponse<CourseListItem> response = courseService.getCourses(emptyCondition(), UUID.randomUUID(), pageable);

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

        PageResponse<CourseListItem> response = courseService.getCourses(emptyCondition(), memberId, pageable);
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
    @DisplayName("코스 목록 조회 성공 - 페이지 메타 정보가 올바르게 반환된다")
    void getCourses_pageMetadataCorrect() {
        Pageable pageable = PageRequest.of(1, 5);
        List<CourseListQueryResult> content = List.of(
                sampleQueryResult(UUID.randomUUID()),
                sampleQueryResult(UUID.randomUUID())
        );

        given(courseRepository.findCourses(any(), any())).willReturn(new PageImpl<>(content, pageable, 12));
        given(courseWishlistRepository.findWishlistedCourseIds(any(), any())).willReturn(List.of());

        PageResponse<CourseListItem> response = courseService.getCourses(emptyCondition(), UUID.randomUUID(), pageable);

        assertThat(response.currentPage()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(5);
        assertThat(response.totalElements()).isEqualTo(12);
        assertThat(response.totalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("코스 상세 조회 성공 - 모든 필드가 올바르게 매핑된다")
    void getCourseDetail_success_allFieldsMapped() {
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Course course = openCourseWithId(courseId);
        CourseCategory category = CourseCategoryFixture.defaultCourseCategory();
        InstructorMember instructorMember = approvedInstructorMember();
        Member instructorInfo = instructorMember();

        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));
        given(courseCategoryRepository.findById(course.getCourseCategoryId())).willReturn(Optional.of(category));
        given(instructorMemberRepository.findById(course.getMemberInstructorId())).willReturn(Optional.of(instructorMember));
        given(memberRepository.findById(instructorMember.getMemberId())).willReturn(Optional.of(instructorInfo));
        given(reviewRepository.findAverageRatingByMemberInstructorId(instructorMember.getId())).willReturn(4.5);
        given(reviewRepository.findAverageRatingByCourseId(courseId)).willReturn(4.2);
        given(reviewRepository.countByCourseId(courseId)).willReturn(15L);
        given(courseWishlistRepository.existsByMemberIdAndCourseId(memberId, courseId)).willReturn(true);

        CourseDetailResponse response = courseService.getCourseDetail(courseId, memberId);

        assertThat(response.courseId()).isEqualTo(courseId);
        assertThat(response.title()).isEqualTo(CourseFixture.DEFAULT_TITLE);
        assertThat(response.description()).isEqualTo(CourseFixture.DEFAULT_DESCRIPTION);
        assertThat(response.categoryCode()).isEqualTo(CourseCategoryFixture.DEFAULT_CODE);
        assertThat(response.categoryName()).isEqualTo(CourseCategoryFixture.DEFAULT_NAME);
        assertThat(response.level()).isEqualTo(CourseFixture.DEFAULT_LEVEL);
        assertThat(response.price()).isEqualTo(CourseFixture.DEFAULT_PRICE);
        assertThat(response.averageRating()).isEqualTo(4.2);
        assertThat(response.reviewCount()).isEqualTo(15L);
        assertThat(response.isWishlisted()).isTrue();
        assertThat(response.instructor().averageRating()).isEqualTo(4.5);
        assertThat(response.instructor().memberId()).isEqualTo(instructorInfo.getId());
    }

    @Test
    @DisplayName("코스 상세 조회 성공 - 리뷰가 없으면 평점과 리뷰 수가 0")
    void getCourseDetail_noReviews_returnsZeroStats() {
        UUID courseId = UUID.randomUUID();
        Course course = openCourseWithId(courseId);
        InstructorMember instructorMember = approvedInstructorMember();

        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));
        given(courseCategoryRepository.findById(any())).willReturn(Optional.of(CourseCategoryFixture.defaultCourseCategory()));
        given(instructorMemberRepository.findById(any())).willReturn(Optional.of(instructorMember));
        given(memberRepository.findById(any())).willReturn(Optional.of(instructorMember()));
        given(reviewRepository.findAverageRatingByMemberInstructorId(any())).willReturn(null);
        given(reviewRepository.findAverageRatingByCourseId(courseId)).willReturn(null);
        given(reviewRepository.countByCourseId(courseId)).willReturn(0L);
        given(courseWishlistRepository.existsByMemberIdAndCourseId(any(), any())).willReturn(false);

        CourseDetailResponse response = courseService.getCourseDetail(courseId, UUID.randomUUID());

        assertThat(response.averageRating()).isZero();
        assertThat(response.reviewCount()).isZero();
        assertThat(response.instructor().averageRating()).isZero();
    }

    @Test
    @DisplayName("코스 상세 조회 성공 - 비인증 유저(memberId=null)이면 isWishlisted=false이고 위시리스트 조회 안 함")
    void getCourseDetail_notAuthenticated_isWishlistedFalse_noWishlistQuery() {
        UUID courseId = UUID.randomUUID();
        Course course = openCourseWithId(courseId);
        InstructorMember instructorMember = approvedInstructorMember();

        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));
        given(courseCategoryRepository.findById(any())).willReturn(Optional.of(CourseCategoryFixture.defaultCourseCategory()));
        given(instructorMemberRepository.findById(any())).willReturn(Optional.of(instructorMember));
        given(memberRepository.findById(any())).willReturn(Optional.of(instructorMember()));
        given(reviewRepository.findAverageRatingByMemberInstructorId(any())).willReturn(null);
        given(reviewRepository.findAverageRatingByCourseId(courseId)).willReturn(null);
        given(reviewRepository.countByCourseId(courseId)).willReturn(0L);

        CourseDetailResponse response = courseService.getCourseDetail(courseId, null);

        assertThat(response.isWishlisted()).isFalse();
        verify(courseWishlistRepository, never()).existsByMemberIdAndCourseId(any(), any());
    }

    @Test
    @DisplayName("코스 상세 조회 성공 - 코스 이미지 URL이 응답에 포함된다")
    void getCourseDetail_imagesIncludedInResponse() {
        UUID courseId = UUID.randomUUID();
        Course course = openCourseWithId(courseId);
        CourseImage image1 = CourseImage.register(course, "https://cdn.example.com/img1.jpg");
        CourseImage image2 = CourseImage.register(course, "https://cdn.example.com/img2.jpg");

        ReflectionTestUtils.setField(image1, "id", UUID.fromString("00000000-0000-7000-8000-000000000001"));
        ReflectionTestUtils.setField(image2, "id", UUID.fromString("00000000-0000-7000-8000-000000000002"));
        ReflectionTestUtils.setField(course, "images", List.of(image1, image2));

        InstructorMember instructorMember = approvedInstructorMember();
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));
        given(courseCategoryRepository.findById(any())).willReturn(Optional.of(CourseCategoryFixture.defaultCourseCategory()));
        given(instructorMemberRepository.findById(any())).willReturn(Optional.of(instructorMember));
        given(memberRepository.findById(any())).willReturn(Optional.of(instructorMember()));
        given(reviewRepository.findAverageRatingByMemberInstructorId(any())).willReturn(null);
        given(reviewRepository.findAverageRatingByCourseId(courseId)).willReturn(null);
        given(reviewRepository.countByCourseId(courseId)).willReturn(0L);
        given(courseWishlistRepository.existsByMemberIdAndCourseId(any(), any())).willReturn(false);

        CourseDetailResponse response = courseService.getCourseDetail(courseId, UUID.randomUUID());

        assertThat(response.images()).containsExactly(
                "https://cdn.example.com/img1.jpg",
                "https://cdn.example.com/img2.jpg"
        );
    }

    @Test
    @DisplayName("코스 상세 조회 실패 - 존재하지 않는 코스 ID이면 NOT_FOUND")
    void getCourseDetail_courseNotFound_throwsNotFound() {
        UUID courseId = UUID.randomUUID();
        given(courseRepository.findById(courseId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.getCourseDetail(courseId, UUID.randomUUID()))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 코스입니다");

        verify(courseCategoryRepository, never()).findById(any());
    }

    @Test
    @DisplayName("코스 상세 조회 실패 - PREPARATION 상태 코스는 공개 조회 불가 (NOT_FOUND)")
    void getCourseDetail_preparationCourse_treatedAsNotFound() {
        UUID courseId = UUID.randomUUID();
        Course preparationCourse = courseWithId(courseId);

        given(courseRepository.findById(courseId)).willReturn(Optional.of(preparationCourse));

        assertThatThrownBy(() -> courseService.getCourseDetail(courseId, UUID.randomUUID()))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 코스입니다");

        verify(courseCategoryRepository, never()).findById(any());
    }

    @Test
    @DisplayName("코스 상세 조회 실패 - 카테고리를 찾을 수 없으면 NOT_FOUND")
    void getCourseDetail_categoryNotFound_throwsNotFound() {
        UUID courseId = UUID.randomUUID();
        Course course = openCourseWithId(courseId);

        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));
        given(courseCategoryRepository.findById(course.getCourseCategoryId())).willReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.getCourseDetail(courseId, UUID.randomUUID()))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 카테고리입니다");

        verify(instructorMemberRepository, never()).findById(any());
    }

    @Test
    @DisplayName("코스 상세 조회 실패 - 강사 엔티티가 없으면 NOT_FOUND")
    void getCourseDetail_instructorNotFound_throwsNotFound() {
        UUID courseId = UUID.randomUUID();
        Course course = openCourseWithId(courseId);

        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));
        given(courseCategoryRepository.findById(any())).willReturn(Optional.of(CourseCategoryFixture.defaultCourseCategory()));
        given(instructorMemberRepository.findById(course.getMemberInstructorId())).willReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.getCourseDetail(courseId, UUID.randomUUID()))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 강사입니다");

        verify(memberRepository, never()).findById(any());
    }

    @Test
    @DisplayName("코스 상세 조회 실패 - 강사 회원 정보가 없으면 NOT_FOUND")
    void getCourseDetail_instructorMemberNotFound_throwsNotFound() {
        UUID courseId = UUID.randomUUID();
        Course course = openCourseWithId(courseId);
        InstructorMember instructorMember = approvedInstructorMember();

        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));
        given(courseCategoryRepository.findById(any())).willReturn(Optional.of(CourseCategoryFixture.defaultCourseCategory()));
        given(instructorMemberRepository.findById(any())).willReturn(Optional.of(instructorMember));
        given(memberRepository.findById(instructorMember.getMemberId())).willReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.getCourseDetail(courseId, UUID.randomUUID()))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 회원입니다");

        verify(reviewRepository, never()).findAverageRatingByCourseId(any());
    }

    @Test
    @DisplayName("내 코스 목록 조회 성공 - PREPARATION 포함 전체 코스 반환")
    void getMyInstructorCourses_success_includesPreparation() {
        UUID memberId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        InstructorMember instructor = approvedInstructorMember();

        InstructorCourseQueryResult openResult = sampleInstructorCourseQueryResult(CourseStatus.OPEN);
        InstructorCourseQueryResult preparationResult = sampleInstructorCourseQueryResult(CourseStatus.PREPARATION);

        given(instructorMemberRepository.findByMemberId(memberId)).willReturn(Optional.of(instructor));
        given(courseRepository.findMyCoursesByInstructorMemberId(instructor.getId(), pageable))
                .willReturn(new PageImpl<>(List.of(openResult, preparationResult), pageable, 2));

        PageResponse<InstructorCourseListItem> response = courseService.getMyInstructorCourses(memberId, pageable);

        assertThat(response.content()).hasSize(2);
        assertThat(response.content().stream().map(item -> item.status()).toList())
                .containsExactlyInAnyOrder(CourseStatus.OPEN, CourseStatus.PREPARATION);
        assertThat(response.totalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("내 코스 목록 조회 성공 - 페이징 메타 정보가 올바르게 반환된다")
    void getMyInstructorCourses_pagingMetadata_correct() {
        UUID memberId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 2);
        InstructorMember instructor = approvedInstructorMember();

        List<InstructorCourseQueryResult> items = List.of(
                sampleInstructorCourseQueryResult(CourseStatus.OPEN),
                sampleInstructorCourseQueryResult(CourseStatus.PREPARATION)
        );
        given(instructorMemberRepository.findByMemberId(memberId)).willReturn(Optional.of(instructor));
        given(courseRepository.findMyCoursesByInstructorMemberId(instructor.getId(), pageable))
                .willReturn(new PageImpl<>(items, pageable, 5));

        PageResponse<InstructorCourseListItem> response = courseService.getMyInstructorCourses(memberId, pageable);

        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.isLast()).isFalse();
    }

    @Test
    @DisplayName("내 코스 목록 조회 실패 - 강사 등록이 없으면 ServiceErrorException 발생")
    void getMyInstructorCourses_instructorNotFound_throwsException() {
        UUID memberId = UUID.randomUUID();
        given(instructorMemberRepository.findByMemberId(memberId)).willReturn(Optional.empty());

        assertThatThrownBy(() ->
                courseService.getMyInstructorCourses(memberId, PageRequest.of(0, 10))
        )
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 강사입니다");
    }

    @Test
    @DisplayName("내 코스 목록 조회 실패 - PENDING/REJECTED 강사는 ServiceErrorException 발생")
    void getMyInstructorCourses_notApprovedInstructor_throwsException() {
        UUID memberId = UUID.randomUUID();
        InstructorMember pendingInstructor = InstructorMemberFixture.defaultInstructorMember();

        given(instructorMemberRepository.findByMemberId(memberId)).willReturn(Optional.of(pendingInstructor));

        assertThatThrownBy(() ->
                courseService.getMyInstructorCourses(memberId, PageRequest.of(0, 10))
        )
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 강사입니다");
    }

    @Test
    @DisplayName("내 코스 목록 조회 성공 - 코스가 없으면 빈 페이지 반환")
    void getMyInstructorCourses_noCourses_returnsEmptyPage() {
        UUID memberId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        InstructorMember instructor = approvedInstructorMember();

        given(instructorMemberRepository.findByMemberId(memberId)).willReturn(Optional.of(instructor));
        given(courseRepository.findMyCoursesByInstructorMemberId(instructor.getId(), pageable))
                .willReturn(new PageImpl<>(List.of(), pageable, 0));

        PageResponse<InstructorCourseListItem> response = courseService.getMyInstructorCourses(memberId, pageable);

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
        assertThat(response.isLast()).isTrue();
    }

    @Test
    @DisplayName("수강생 명단 조회 성공 - CONFIRMED 수강생 목록과 출석 정보 반환")
    void getCourseStudents_success() {
        UUID memberId = UUID.randomUUID();
        UUID courseId = CourseFixture.DEFAULT_COURSE_CATEGORY_ID;
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

        PageResponse<CourseStudentItem> response = courseService.getCourseStudents(courseId, memberId, pageable);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).memberName()).isEqualTo("김수강");
        assertThat(response.content().get(0).attendanceStatus()).isEqualTo(AttendanceStatus.ATTEND);
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("수강생 명단 조회 성공 - 출석 정보가 없는 수강생(ABSENT)도 포함")
    void getCourseStudents_success_withAbsentStudent() {
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);

        InstructorMember instructor = approvedInstructorMember();
        Course course = openCourseWithId(courseId);
        ReflectionTestUtils.setField(course, "memberInstructorId", instructor.getId());

        CourseStudentQueryResult absent = new CourseStudentQueryResult(
                UUID.randomUUID(), "이결석", AttendanceStatus.ABSENT, null
        );

        given(instructorMemberRepository.findByMemberId(memberId)).willReturn(Optional.of(instructor));
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));
        given(orderRepository.findConfirmedStudentsByCourseId(courseId, pageable))
                .willReturn(new PageImpl<>(List.of(absent), pageable, 1));

        PageResponse<CourseStudentItem> response = courseService.getCourseStudents(courseId, memberId, pageable);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).attendanceStatus()).isEqualTo(AttendanceStatus.ABSENT);
        assertThat(response.content().get(0).attendanceAt()).isNull();
    }

    @Test
    @DisplayName("수강생 명단 조회 성공 - 수강생이 없으면 빈 페이지 반환")
    void getCourseStudents_empty() {
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);

        InstructorMember instructor = approvedInstructorMember();
        Course course = openCourseWithId(courseId);
        ReflectionTestUtils.setField(course, "memberInstructorId", instructor.getId());

        given(instructorMemberRepository.findByMemberId(memberId)).willReturn(Optional.of(instructor));
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));
        given(orderRepository.findConfirmedStudentsByCourseId(courseId, pageable))
                .willReturn(new PageImpl<>(List.of(), pageable, 0));

        PageResponse<CourseStudentItem> response = courseService.getCourseStudents(courseId, memberId, pageable);

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
        assertThat(response.isLast()).isTrue();
    }

    @Test
    @DisplayName("수강생 명단 조회 실패 - 강사 등록이 없으면 ERR_NOT_FOUND_INSTRUCTOR")
    void getCourseStudents_instructorNotFound_throwsException() {
        UUID memberId = UUID.randomUUID();
        given(instructorMemberRepository.findByMemberId(memberId)).willReturn(Optional.empty());

        assertThatThrownBy(() ->
                courseService.getCourseStudents(UUID.randomUUID(), memberId, PageRequest.of(0, 10))
        )
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 강사입니다");

        verify(courseRepository, never()).findById(any());
    }

    @Test
    @DisplayName("수강생 명단 조회 실패 - 미승인 강사(PENDING)는 ERR_NOT_FOUND_INSTRUCTOR")
    void getCourseStudents_notApprovedInstructor_throwsException() {
        UUID memberId = UUID.randomUUID();
        InstructorMember pendingInstructor = InstructorMemberFixture.defaultInstructorMember();

        given(instructorMemberRepository.findByMemberId(memberId)).willReturn(Optional.of(pendingInstructor));

        assertThatThrownBy(() ->
                courseService.getCourseStudents(UUID.randomUUID(), memberId, PageRequest.of(0, 10))
        )
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 강사입니다");

        verify(courseRepository, never()).findById(any());
    }

    @Test
    @DisplayName("수강생 명단 조회 실패 - 코스가 없으면 ERR_NOT_FOUND_COURSE")
    void getCourseStudents_courseNotFound_throwsException() {
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        InstructorMember instructor = approvedInstructorMember();

        given(instructorMemberRepository.findByMemberId(memberId)).willReturn(Optional.of(instructor));
        given(courseRepository.findById(courseId)).willReturn(Optional.empty());

        assertThatThrownBy(() ->
                courseService.getCourseStudents(courseId, memberId, PageRequest.of(0, 10))
        )
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 코스입니다");

        verify(orderRepository, never()).findConfirmedStudentsByCourseId(any(), any());
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
                courseService.getCourseStudents(courseId, memberId, PageRequest.of(0, 10))
        )
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("본인 코스만 조회 가능합니다");

        verify(orderRepository, never()).findConfirmedStudentsByCourseId(any(), any());
    }

    @Test
    @DisplayName("수강생 명단 조회 실패 - PREPARATION 코스는 ERR_COURSE_IN_PREPARATION")
    void getCourseStudents_preparationCourse_throwsBadRequest() {
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        InstructorMember instructor = approvedInstructorMember();

        Course course = courseWithId(courseId);
        ReflectionTestUtils.setField(course, "memberInstructorId", instructor.getId());

        given(instructorMemberRepository.findByMemberId(memberId)).willReturn(Optional.of(instructor));
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));

        assertThatThrownBy(() ->
                courseService.getCourseStudents(courseId, memberId, PageRequest.of(0, 10))
        )
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("준비 중인 코스는 수강생을 조회할 수 없습니다");

        verify(orderRepository, never()).findConfirmedStudentsByCourseId(any(), any());
    }

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

        CreateCourseRequestResponse response = courseService.createCourseRequest(memberId, request);

        assertThat(response.title()).isEqualTo(request.title());
        assertThat(response.categoryCode()).isEqualTo(CourseCategoryFixture.DEFAULT_CODE);
        assertThat(response.status()).isEqualTo(CourseStatus.PREPARATION);
        verify(courseRepository).save(any(Course.class));
        verify(courseImageRepository).saveAll(any());
    }

    @Test
    @DisplayName("코스 개설 신청 성공 - imageUrls가 null이면 이미지 저장 안 함")
    void createCourseRequest_noImages() {
        UUID memberId = UUID.randomUUID();
        InstructorMember instructor = approvedInstructorMember();
        CourseCategory category = CourseCategoryFixture.defaultCourseCategory();
        CreateCourseRequestRequest request = defaultCreateRequest(null);

        given(instructorMemberRepository.findByMemberId(memberId)).willReturn(Optional.of(instructor));
        given(courseCategoryRepository.findByCode(instructor.getCategoryCode())).willReturn(Optional.of(category));
        given(courseRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        courseService.createCourseRequest(memberId, request);

        verify(courseImageRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("코스 개설 신청 실패 - 미승인 강사이면 ERR_NOT_FOUND_INSTRUCTOR")
    void createCourseRequest_notApprovedInstructor() {
        UUID memberId = UUID.randomUUID();
        InstructorMember pending = InstructorMemberFixture.defaultInstructorMember();
        given(instructorMemberRepository.findByMemberId(memberId)).willReturn(Optional.of(pending));

        assertThatThrownBy(() -> courseService.createCourseRequest(memberId, defaultCreateRequest(null)))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 강사입니다");

        verify(courseRepository, never()).save(any());
    }

    @Test
    @DisplayName("코스 개설 신청 실패 - 잘못된 강의 시간 구간이면 ERR_INVALID_SCHEDULE")
    void createCourseRequest_invalidSchedule() {
        UUID memberId = UUID.randomUUID();
        InstructorMember instructor = approvedInstructorMember();
        CourseCategory category = CourseCategoryFixture.defaultCourseCategory();

        LocalDateTime startAt = LocalDateTime.now().plusDays(30).withHour(18).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime endAt = startAt.withHour(9);
        CreateCourseRequestRequest request = new CreateCourseRequestRequest(
                "제목", "설명", "주소", "상세주소",
                BigInteger.valueOf(50000), 10,
                LocalDateTime.now().plusDays(10),
                LocalDateTime.now().plusDays(20),
                startAt, endAt,
                CourseLevel.BEGINNER, null
        );

        given(instructorMemberRepository.findByMemberId(memberId)).willReturn(Optional.of(instructor));
        given(courseCategoryRepository.findByCode(instructor.getCategoryCode())).willReturn(Optional.of(category));

        assertThatThrownBy(() -> courseService.createCourseRequest(memberId, request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("코스의 종료 일시는 코스의 시작 일시보다 이후여야 합니다");
    }

    @Test
    @DisplayName("코스 개설 신청 실패 - 잘못된 주문 마감 시간이면 ERR_INVALID_ORDER_CLOSE_TIME")
    void createCourseRequest_invalidOrderCloseTime() {
        UUID memberId = UUID.randomUUID();
        InstructorMember instructor = approvedInstructorMember();
        CourseCategory category = CourseCategoryFixture.defaultCourseCategory();

        LocalDateTime startAt = LocalDateTime.now().plusDays(30);
        LocalDateTime orderCloseAt = startAt.minusHours(1);
        CreateCourseRequestRequest request = new CreateCourseRequestRequest(
                "제목", "설명", "주소", "상세주소",
                BigInteger.valueOf(50000), 10,
                LocalDateTime.now().plusDays(10),
                orderCloseAt,
                startAt, startAt.plusHours(2),
                CourseLevel.BEGINNER, null
        );

        given(instructorMemberRepository.findByMemberId(memberId)).willReturn(Optional.of(instructor));
        given(courseCategoryRepository.findByCode(instructor.getCategoryCode())).willReturn(Optional.of(category));

        assertThatThrownBy(() -> courseService.createCourseRequest(memberId, request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("코스의 주문 마감 시간은 코스의 주문가능 시작 시각부터 코스의 시작일시 2시간 전 까지 가능합니다");
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

        courseService.deleteCourseRequest(memberId, courseId);

        verify(courseRepository).delete(course);
    }

    @Test
    @DisplayName("코스 개설 신청 취소 실패 - 미승인 강사이면 ERR_NOT_FOUND_INSTRUCTOR")
    void deleteCourseRequest_notApprovedInstructor() {
        UUID memberId = UUID.randomUUID();
        InstructorMember pending = InstructorMemberFixture.defaultInstructorMember();
        given(instructorMemberRepository.findByMemberId(memberId)).willReturn(Optional.of(pending));

        assertThatThrownBy(() -> courseService.deleteCourseRequest(memberId, UUID.randomUUID()))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 강사입니다");

        verify(courseRepository, never()).findById(any());
    }

    @Test
    @DisplayName("코스 개설 신청 취소 실패 - 존재하지 않는 코스이면 ERR_NOT_FOUND_COURSE")
    void deleteCourseRequest_courseNotFound() {
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        InstructorMember instructor = approvedInstructorMember();

        given(instructorMemberRepository.findByMemberId(memberId)).willReturn(Optional.of(instructor));
        given(courseRepository.findById(courseId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.deleteCourseRequest(memberId, courseId))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 코스입니다");

        verify(courseRepository, never()).delete(any());
    }

    @Test
    @DisplayName("코스 개설 신청 취소 실패 - 본인 코스가 아니면 ERR_FORBIDDEN_COURSE_DELETE")
    void deleteCourseRequest_notOwnCourse() {
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        InstructorMember instructor = approvedInstructorMember();
        Course course = courseWithId(courseId);
        ReflectionTestUtils.setField(course, "memberInstructorId", UUID.randomUUID());

        given(instructorMemberRepository.findByMemberId(memberId)).willReturn(Optional.of(instructor));
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));

        assertThatThrownBy(() -> courseService.deleteCourseRequest(memberId, courseId))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("본인 코스만 삭제 가능합니다");

        verify(courseRepository, never()).delete(any());
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

        assertThatThrownBy(() -> courseService.deleteCourseRequest(memberId, courseId))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("PREPARATION 상태의 코스만 삭제할 수 있습니다");

        verify(courseRepository, never()).delete(any());
    }

    @Test
    @DisplayName("찜 등록 성공 - OPEN 코스이면 isWishlisted=true 반환")
    void addWishlist_success() {
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Course course = openCourseWithId(courseId);

        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));
        given(courseWishlistRepository.existsByMemberIdAndCourseId(memberId, courseId)).willReturn(false);

        CourseWishlistResponse response = courseService.addWishlist(memberId, courseId);

        assertThat(response.courseId()).isEqualTo(courseId);
        assertThat(response.isWishlisted()).isTrue();
        verify(courseWishlistRepository).save(any());
    }

    @Test
    @DisplayName("찜 등록 실패 - 존재하지 않는 코스이면 ERR_NOT_FOUND_COURSE")
    void addWishlist_courseNotFound() {
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();

        given(courseRepository.findById(courseId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.addWishlist(memberId, courseId))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 코스입니다");

        verify(courseWishlistRepository, never()).save(any());
    }

    @Test
    @DisplayName("찜 등록 실패 - OPEN 상태가 아닌 코스이면 ERR_NOT_FOUND_COURSE (404)")
    void addWishlist_courseNotOpen() {
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Course course = courseWithId(courseId);

        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));

        assertThatThrownBy(() -> courseService.addWishlist(memberId, courseId))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 코스입니다");

        verify(courseWishlistRepository, never()).save(any());
    }

    @Test
    @DisplayName("찜 등록 실패 - 이미 찜한 코스이면 ERR_ALREADY_WISHLISTED")
    void addWishlist_alreadyWishlisted() {
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Course course = openCourseWithId(courseId);

        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));
        given(courseWishlistRepository.existsByMemberIdAndCourseId(memberId, courseId)).willReturn(true);

        assertThatThrownBy(() -> courseService.addWishlist(memberId, courseId))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("이미 찜한 코스입니다");

        verify(courseWishlistRepository, never()).save(any());
    }

    @Test
    @DisplayName("찜 해제 성공 - isWishlisted=false 반환")
    void removeWishlist_success() {
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        four_tential.potential.domain.course.course_wishlist.CourseWishlist wishlist =
                four_tential.potential.domain.course.course_wishlist.CourseWishlist.register(memberId, courseId);

        given(courseWishlistRepository.findByMemberIdAndCourseId(memberId, courseId))
                .willReturn(Optional.of(wishlist));

        CourseWishlistResponse response = courseService.removeWishlist(memberId, courseId);

        assertThat(response.courseId()).isEqualTo(courseId);
        assertThat(response.isWishlisted()).isFalse();
        verify(courseWishlistRepository).delete(wishlist);
    }

    @Test
    @DisplayName("찜 해제 실패 - 찜 목록에 없으면 ERR_WISHLIST_NOT_FOUND")
    void removeWishlist_notFound() {
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();

        given(courseWishlistRepository.findByMemberIdAndCourseId(memberId, courseId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.removeWishlist(memberId, courseId))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("찜 목록에 존재하지 않는 코스입니다");

        verify(courseWishlistRepository, never()).delete(any());
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

        courseService.closeCourse(memberId, courseId);

        assertThat(course.getStatus()).isEqualTo(CourseStatus.CLOSED);
        verify(courseWishlistRepository).deleteByCourseId(courseId);
    }

    @Test
    @DisplayName("코스 종료 실패 - 강사 등록이 없으면 ERR_NOT_FOUND_INSTRUCTOR")
    void closeCourse_instructorNotFound() {
        UUID memberId = UUID.randomUUID();
        given(instructorMemberRepository.findByMemberId(memberId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.closeCourse(memberId, UUID.randomUUID()))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 강사입니다");

        verify(courseRepository, never()).findById(any());
    }

    @Test
    @DisplayName("코스 종료 실패 - 미승인 강사(PENDING)이면 ERR_NOT_FOUND_INSTRUCTOR")
    void closeCourse_notApprovedInstructor() {
        UUID memberId = UUID.randomUUID();
        InstructorMember pendingInstructor = InstructorMemberFixture.defaultInstructorMember();

        given(instructorMemberRepository.findByMemberId(memberId)).willReturn(Optional.of(pendingInstructor));

        assertThatThrownBy(() -> courseService.closeCourse(memberId, UUID.randomUUID()))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 강사입니다");

        verify(courseRepository, never()).findById(any());
    }

    @Test
    @DisplayName("코스 종료 실패 - 존재하지 않는 코스이면 ERR_NOT_FOUND_COURSE")
    void closeCourse_courseNotFound() {
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        InstructorMember instructor = approvedInstructorMember();

        given(instructorMemberRepository.findByMemberId(memberId)).willReturn(Optional.of(instructor));
        given(courseRepository.findById(courseId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.closeCourse(memberId, courseId))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 코스입니다");

        verify(courseWishlistRepository, never()).deleteByCourseId(any());
    }

    @Test
    @DisplayName("코스 종료 실패 - 본인 코스가 아니면 ERR_FORBIDDEN_COURSE_CLOSE")
    void closeCourse_notOwnCourse() {
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        InstructorMember instructor = approvedInstructorMember();
        Course course = openCourseWithId(courseId);
        ReflectionTestUtils.setField(course, "memberInstructorId", UUID.randomUUID());

        given(instructorMemberRepository.findByMemberId(memberId)).willReturn(Optional.of(instructor));
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));

        assertThatThrownBy(() -> courseService.closeCourse(memberId, courseId))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("본인 코스만 종료 가능합니다");

        verify(courseWishlistRepository, never()).deleteByCourseId(any());
    }

    @Test
    @DisplayName("코스 종료 실패 - OPEN이 아닌 코스(PREPARATION)이면 ERR_INVALID_STATUS_TRANSITION_TO_CLOSE")
    void closeCourse_notOpenCourse() {
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        InstructorMember instructor = approvedInstructorMember();
        Course course = courseWithId(courseId);
        ReflectionTestUtils.setField(course, "memberInstructorId", instructor.getId());

        given(instructorMemberRepository.findByMemberId(memberId)).willReturn(Optional.of(instructor));
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));

        assertThatThrownBy(() -> courseService.closeCourse(memberId, courseId))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("OPEN 상태의 코스만 CLOSE 할 수 있습니다");

        verify(courseWishlistRepository, never()).deleteByCourseId(any());
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

        UpdateCourseResponse response = courseService.updateCourse(memberId, courseId, defaultUpdateRequest());

        assertThat(response.courseId()).isEqualTo(courseId);
        assertThat(response.title()).isEqualTo("수정된 제목");
        verify(courseImageRepository).saveAll(any());
    }

    @Test
    @DisplayName("코스 수정 성공 - imageUrls가 null이면 이미지 변경 없음")
    void updateCourse_nullImageUrls_imagesUnchanged() {
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        InstructorMember instructor = approvedInstructorMember();
        Course course = courseWithId(courseId);
        ReflectionTestUtils.setField(course, "memberInstructorId", instructor.getId());

        UpdateCourseRequest request = new UpdateCourseRequest(
                "수정된 제목", "수정된 설명입니다.",
                CourseLevel.INTERMEDIATE,
                "서울시 서초구 강남대로 456", "2층 필라테스 스튜디오",
                BigInteger.valueOf(65000), 12,
                LocalDateTime.now().plusDays(10),
                LocalDateTime.now().plusDays(20),
                LocalDateTime.now().plusDays(30),
                LocalDateTime.now().plusDays(30).plusHours(2),
                null
        );

        given(instructorMemberRepository.findByMemberId(memberId)).willReturn(Optional.of(instructor));
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));

        courseService.updateCourse(memberId, courseId, request);

        verify(courseImageRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("코스 수정 성공 - imageUrls가 빈 배열이면 기존 이미지 전체 삭제")
    void updateCourse_emptyImageUrls_clearsImages() {
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        InstructorMember instructor = approvedInstructorMember();
        Course course = courseWithId(courseId);
        ReflectionTestUtils.setField(course, "memberInstructorId", instructor.getId());

        UpdateCourseRequest request = new UpdateCourseRequest(
                "수정된 제목", "수정된 설명입니다.",
                CourseLevel.INTERMEDIATE,
                "서울시 서초구 강남대로 456", "2층 필라테스 스튜디오",
                BigInteger.valueOf(65000), 12,
                LocalDateTime.now().plusDays(10),
                LocalDateTime.now().plusDays(20),
                LocalDateTime.now().plusDays(30),
                LocalDateTime.now().plusDays(30).plusHours(2),
                List.of()
        );

        given(instructorMemberRepository.findByMemberId(memberId)).willReturn(Optional.of(instructor));
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));

        courseService.updateCourse(memberId, courseId, request);

        verify(courseImageRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("코스 수정 성공 - REJECTED 코스도 모든 필드 수정 가능")
    void updateCourse_rejected_success() {
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        InstructorMember instructor = approvedInstructorMember();
        Course course = courseWithId(courseId);
        course.reject("사진 자료 미비");
        ReflectionTestUtils.setField(course, "memberInstructorId", instructor.getId());

        given(instructorMemberRepository.findByMemberId(memberId)).willReturn(Optional.of(instructor));
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));
        given(courseImageRepository.saveAll(any())).willAnswer(invocation -> invocation.getArgument(0));

        UpdateCourseResponse response = courseService.updateCourse(memberId, courseId, defaultUpdateRequest());

        assertThat(response.title()).isEqualTo("수정된 제목");
    }

    @Test
    @DisplayName("코스 수정 성공 - OPEN 코스는 제목, 설명, 이미지만 수정된다")
    void updateCourse_open_basicFieldsOnly() {
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        InstructorMember instructor = approvedInstructorMember();
        Course course = openCourseWithId(courseId);
        ReflectionTestUtils.setField(course, "memberInstructorId", instructor.getId());

        UpdateCourseRequest request = new UpdateCourseRequest(
                "수정된 제목", "수정된 설명",
                null, null, null, null, null,
                null, null, null, null,
                List.of("https://cdn.example.com/new.jpg")
        );

        given(instructorMemberRepository.findByMemberId(memberId)).willReturn(Optional.of(instructor));
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));
        given(courseImageRepository.saveAll(any())).willAnswer(invocation -> invocation.getArgument(0));

        UpdateCourseResponse response = courseService.updateCourse(memberId, courseId, request);

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

        assertThatThrownBy(() -> courseService.updateCourse(memberId, courseId, defaultUpdateRequest()))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("OPEN 상태에서는 가격, 일정, 장소, 정원을 수정할 수 없습니다");

        verify(courseImageRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("코스 수정 실패 - CLOSED 코스는 ERR_CANNOT_MODIFY_COURSE")
    void updateCourse_closed_throwsConflict() {
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        InstructorMember instructor = approvedInstructorMember();
        Course course = openCourseWithId(courseId);
        course.close();
        ReflectionTestUtils.setField(course, "memberInstructorId", instructor.getId());

        given(instructorMemberRepository.findByMemberId(memberId)).willReturn(Optional.of(instructor));
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));

        assertThatThrownBy(() -> courseService.updateCourse(memberId, courseId, defaultUpdateRequest()))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("CLOSED 또는 CANCELLED 상태의 코스는 수정할 수 없습니다");
    }

    @Test
    @DisplayName("코스 수정 실패 - 본인 코스가 아니면 ERR_FORBIDDEN_COURSE_MODIFY")
    void updateCourse_notOwnCourse_throwsForbidden() {
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        InstructorMember instructor = approvedInstructorMember();
        Course course = courseWithId(courseId);
        ReflectionTestUtils.setField(course, "memberInstructorId", UUID.randomUUID());

        given(instructorMemberRepository.findByMemberId(memberId)).willReturn(Optional.of(instructor));
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));

        assertThatThrownBy(() -> courseService.updateCourse(memberId, courseId, defaultUpdateRequest()))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("본인 코스만 수정 가능합니다");
    }

    @Test
    @DisplayName("코스 수정 실패 - 존재하지 않는 코스이면 ERR_NOT_FOUND_COURSE")
    void updateCourse_courseNotFound() {
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        InstructorMember instructor = approvedInstructorMember();

        given(instructorMemberRepository.findByMemberId(memberId)).willReturn(Optional.of(instructor));
        given(courseRepository.findById(courseId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.updateCourse(memberId, courseId, defaultUpdateRequest()))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 코스입니다");
    }

    private UpdateCourseRequest defaultUpdateRequest() {
        return new UpdateCourseRequest(
                "수정된 제목",
                "수정된 설명입니다.",
                CourseLevel.INTERMEDIATE,
                "서울시 서초구 강남대로 456",
                "2층 필라테스 스튜디오",
                BigInteger.valueOf(65000),
                12,
                LocalDateTime.now().plusDays(10),
                LocalDateTime.now().plusDays(20),
                LocalDateTime.now().plusDays(30),
                LocalDateTime.now().plusDays(30).plusHours(2),
                List.of("https://cdn.example.com/img1.jpg")
        );
    }

    private CreateCourseRequestRequest defaultCreateRequest(List<String> imageUrls) {
        return new CreateCourseRequestRequest(
                "소도구 필라테스 입문반",
                "소도구를 활용한 전신 필라테스 수업입니다.",
                "서울시 강남구 테헤란로 123",
                "3층 필라테스룸",
                BigInteger.valueOf(70000),
                10,
                LocalDateTime.now().plusDays(10),
                LocalDateTime.now().plusDays(20),
                LocalDateTime.now().plusDays(30),
                LocalDateTime.now().plusDays(30).plusHours(2),
                CourseLevel.BEGINNER,
                imageUrls
        );
    }

    private InstructorCourseQueryResult sampleInstructorCourseQueryResult(CourseStatus status) {
        return new InstructorCourseQueryResult(
                UUID.randomUUID(),
                "테스트 강의",
                CourseLevel.BEGINNER,
                status,
                20,
                5,
                BigInteger.valueOf(50000),
                LocalDateTime.of(2026, 1, 1, 9, 0),
                LocalDateTime.of(2026, 1, 12, 9, 0)
        );
    }

    private CourseSearchCondition emptyCondition() {
        return new CourseSearchCondition(null, null, null, null, null, null, null);
    }

    private CourseListQueryResult sampleQueryResult(UUID courseId) {
        return new CourseListQueryResult(
                courseId,
                "테스트 강의",
                "BACKEND",
                "백엔드",
                UUID.randomUUID(),
                "강사이름",
                "https://cdn.example.com/profile.jpg",
                "https://cdn.example.com/thumb.jpg",
                BigInteger.valueOf(50000),
                20,
                5,
                CourseStatus.OPEN,
                CourseLevel.BEGINNER,
                LocalDateTime.of(2026, 1, 1, 9, 0),
                LocalDateTime.of(2026, 1, 12, 9, 0)
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

    private Member instructorMember() {
        Member member = MemberFixture.defaultMember();
        ReflectionTestUtils.setField(member, "id", InstructorMemberFixture.DEFAULT_MEMBER_ID);
        return member;
    }

    @Test
    @DisplayName("코스 개설 신청 승인 성공 - PREPARATION 코스가 OPEN으로 전이되고 이력 저장")
    void handleCourseRequest_approve_success() {
        UUID courseId = UUID.randomUUID();
        Course course = courseWithId(courseId);

        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));

        CourseRequestActionResponse response =
                courseService.handleCourseRequest(courseId, new CourseRequestActionRequest(CourseApprovalAction.APPROVE, null));

        assertThat(response.courseId()).isEqualTo(courseId);
        assertThat(response.status()).isEqualTo(CourseStatus.OPEN);
        assertThat(response.confirmedAt()).isNotNull();

        ArgumentCaptor<CourseApprovalHistory> captor = ArgumentCaptor.forClass(CourseApprovalHistory.class);
        verify(courseApprovalHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo(CourseApprovalAction.APPROVE);
        assertThat(captor.getValue().getRejectReason()).isNull();
    }

    @Test
    @DisplayName("코스 개설 신청 반려 성공 - REJECTED 상태로 전이, 반려 이력 저장")
    void handleCourseRequest_reject_success() {
        UUID courseId = UUID.randomUUID();
        Course course = courseWithId(courseId);

        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));

        CourseRequestActionResponse response =
                courseService.handleCourseRequest(courseId, new CourseRequestActionRequest(CourseApprovalAction.REJECT, "사진 자료 미비"));

        assertThat(response.courseId()).isEqualTo(courseId);
        assertThat(response.status()).isEqualTo(CourseStatus.REJECTED);
        assertThat(response.confirmedAt()).isNull();

        ArgumentCaptor<CourseApprovalHistory> captor = ArgumentCaptor.forClass(CourseApprovalHistory.class);
        verify(courseApprovalHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo(CourseApprovalAction.REJECT);
        assertThat(captor.getValue().getRejectReason()).isEqualTo("사진 자료 미비");
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

        courseService.reapplyCourseRequest(memberId, courseId);

        assertThat(course.getStatus()).isEqualTo(CourseStatus.PREPARATION);
        assertThat(course.getRejectReason()).isNull();
    }

    @Test
    @DisplayName("코스 개설 재신청 실패 - REJECTED 상태가 아니면 ERR_CANNOT_REAPPLY_COURSE")
    void reapplyCourseRequest_notRejected() {
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        InstructorMember instructor = approvedInstructorMember();
        Course course = courseWithId(courseId);
        ReflectionTestUtils.setField(course, "memberInstructorId", instructor.getId());

        given(instructorMemberRepository.findByMemberId(memberId)).willReturn(Optional.of(instructor));
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));

        assertThatThrownBy(() -> courseService.reapplyCourseRequest(memberId, courseId))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("REJECTED 상태의 코스만 재신청할 수 있습니다");
    }

    @Test
    @DisplayName("코스 개설 신청 반려 실패 - rejectReason 없으면 ERR_REJECT_REASON_REQUIRED")
    void handleCourseRequest_rejectWithoutReason() {
        UUID courseId = UUID.randomUUID();
        Course course = courseWithId(courseId);

        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));

        assertThatThrownBy(() ->
                courseService.handleCourseRequest(courseId, new CourseRequestActionRequest(CourseApprovalAction.REJECT, null))
        )
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("반려 사유는 필수입니다");
    }

    @Test
    @DisplayName("코스 개설 신청 승인/반려 실패 - 존재하지 않는 코스이면 ERR_NOT_FOUND_COURSE")
    void handleCourseRequest_courseNotFound() {
        UUID courseId = UUID.randomUUID();

        given(courseRepository.findById(courseId)).willReturn(Optional.empty());

        assertThatThrownBy(() ->
                courseService.handleCourseRequest(courseId, new CourseRequestActionRequest(CourseApprovalAction.APPROVE, null))
        )
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 코스입니다");
    }

    @Test
    @DisplayName("코스 개설 신청 승인/반려 실패 - PREPARATION이 아니면 ERR_COURSE_NOT_IN_PREPARATION")
    void handleCourseRequest_notPreparation() {
        UUID courseId = UUID.randomUUID();
        Course course = openCourseWithId(courseId);

        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));

        assertThatThrownBy(() ->
                courseService.handleCourseRequest(courseId, new CourseRequestActionRequest(CourseApprovalAction.APPROVE, null))
        )
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("PREPARATION 상태의 코스만 승인 또는 반려할 수 있습니다");
    }
}
