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
import four_tential.potential.domain.course.course_wishlist.CourseWishlistRepository;
import four_tential.potential.domain.course.fixture.CourseCategoryFixture;
import four_tential.potential.domain.course.fixture.CourseFixture;
import four_tential.potential.domain.member.fixture.InstructorMemberFixture;
import four_tential.potential.domain.member.fixture.MemberFixture;
import four_tential.potential.domain.member.instructor_member.InstructorMember;
import four_tential.potential.domain.member.instructor_member.InstructorMemberRepository;
import four_tential.potential.domain.member.member.Member;
import four_tential.potential.domain.member.member.MemberRepository;
import four_tential.potential.domain.review.review.ReviewRepository;
import four_tential.potential.domain.course.course.CourseSearchCondition;
import four_tential.potential.presentation.course.model.response.CourseDetailResponse;
import four_tential.potential.presentation.course.model.response.CourseListItem;
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
class CourseServiceTest {

    @Mock private CourseRepository courseRepository;
    @Mock private CourseCategoryRepository courseCategoryRepository;
    @Mock private CourseWishlistRepository courseWishlistRepository;
    @Mock private InstructorMemberRepository instructorMemberRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private ReviewRepository reviewRepository;

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
                .willReturn(List.of()); // 위시리스트에 없음

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

        // 단위 테스트에서는 JPA가 동작하지 않아 id가 null — 명시적으로 주입 (UUID v7 오름차순 = 등록 순)
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
        Course preparationCourse = courseWithId(courseId); // 기본 상태 = PREPARATION

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

    /** PREPARATION 상태 (Course.register 초기값) */
    private Course courseWithId(UUID courseId) {
        Course course = CourseFixture.defaultCourse();
        ReflectionTestUtils.setField(course, "id", courseId);
        return course;
    }

    /** OPEN 상태로 전환된 코스 */
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
}
