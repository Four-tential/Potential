package four_tential.potential.domain.course.course_wishlist;

import four_tential.potential.domain.course.course.Course;
import four_tential.potential.domain.course.course.CourseLevel;
import four_tential.potential.domain.course.course.CourseRepository;
import four_tential.potential.domain.course.course.CourseStatus;
import four_tential.potential.domain.course.course_category.CourseCategory;
import four_tential.potential.domain.course.course_category.CourseCategoryRepository;
import four_tential.potential.domain.course.course_image.CourseImage;
import four_tential.potential.domain.course.course_image.CourseImageRepository;
import four_tential.potential.domain.member.instructor_member.InstructorMember;
import four_tential.potential.domain.member.instructor_member.InstructorMemberRepository;
import four_tential.potential.domain.member.member.Member;
import four_tential.potential.domain.member.member.MemberRepository;
import four_tential.potential.infra.redis.RedisTestContainer;
import four_tential.potential.presentation.member.model.response.WishlistCourseItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class CourseWishlistQueryRepositoryTest extends RedisTestContainer {

    @Autowired
    private CourseWishlistRepository courseWishlistRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private InstructorMemberRepository instructorMemberRepository;

    @Autowired
    private CourseCategoryRepository courseCategoryRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseImageRepository courseImageRepository;

    private Member student;
    private InstructorMember instructor;
    private CourseCategory category;

    @BeforeEach
    void setUp() {
        student = memberRepository.save(
                Member.register("student@test.com", "encodedPwd!", "수강생", "010-1111-0000")
        );
        Member instructorMember = memberRepository.save(
                Member.register("instructor@test.com", "encodedPwd!", "소강사", "010-2222-0000")
        );
        category = courseCategoryRepository.save(CourseCategory.register("PILATES_TEST", "필라테스테스트"));

        InstructorMember im = InstructorMember.register(
                instructorMember.getId(), "PILATES_TEST", "소개", "https://img.url/cert.jpg"
        );
        im.approve();
        instructor = instructorMemberRepository.save(im);
    }

    // ────────────────────────────────────────────────────────────
    // 기본 조회
    // ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("찜한 코스 3건 조회 - content 3건 반환")
    void findWishlistCourses_three_items() {
        saveWishlist(saveCourse("코스A"));
        saveWishlist(saveCourse("코스B"));
        saveWishlist(saveCourse("코스C"));

        Page<WishlistCourseItem> result =
                courseWishlistRepository.findWishlistCourses(student.getId(), PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("찜한 코스 없으면 빈 페이지 반환")
    void findWishlistCourses_empty() {
        Page<WishlistCourseItem> result =
                courseWishlistRepository.findWishlistCourses(student.getId(), PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("다른 회원의 찜 목록은 내 목록에 포함되지 않음")
    void findWishlistCourses_excludes_other_members() {
        Member otherStudent = memberRepository.save(
                Member.register("other@test.com", "encodedPwd!", "다른학생", "010-9999-0000")
        );
        Course course = saveCourse("다른학생 코스");
        courseWishlistRepository.save(CourseWishlist.register(otherStudent.getId(), course.getId()));

        Page<WishlistCourseItem> result =
                courseWishlistRepository.findWishlistCourses(student.getId(), PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
    }

    // ────────────────────────────────────────────────────────────
    // 응답 필드 검증
    // ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("응답 항목에 courseId, title, memberInstructorName, categoryCode, price, status, startAt, wishedAt 이 올바르게 매핑됨")
    void findWishlistCourses_response_fields_mapped_correctly() {
        Course course = saveCourse("소도구 필라테스 입문반");
        saveWishlist(course);

        WishlistCourseItem item = courseWishlistRepository
                .findWishlistCourses(student.getId(), PageRequest.of(0, 10))
                .getContent().get(0);

        assertThat(item.courseId()).isEqualTo(course.getId());
        assertThat(item.title()).isEqualTo("소도구 필라테스 입문반");
        assertThat(item.memberInstructorName()).isEqualTo("소강사");
        assertThat(item.categoryCode()).isEqualTo("PILATES_TEST");
        assertThat(item.categoryName()).isEqualTo("필라테스테스트");
        assertThat(item.price()).isEqualTo(BigInteger.valueOf(50000));
        assertThat(item.status()).isEqualTo(CourseStatus.PREPARATION);
        assertThat(item.startAt()).isNotNull();
        assertThat(item.wishedAt()).isNotNull();
    }

    // ────────────────────────────────────────────────────────────
    // thumbnail 검증
    // ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("코스 이미지 없으면 thumbnail = null")
    void findWishlistCourses_thumbnail_null_when_no_images() {
        Course course = saveCourse("이미지 없는 코스");
        saveWishlist(course);

        WishlistCourseItem item = courseWishlistRepository
                .findWishlistCourses(student.getId(), PageRequest.of(0, 10))
                .getContent().get(0);

        assertThat(item.thumbnail()).isNull();
    }

    @Test
    @DisplayName("코스 이미지 있으면 thumbnail 반환")
    void findWishlistCourses_thumbnail_returned_when_has_image() {
        Course course = saveCourse("이미지 있는 코스");
        courseImageRepository.save(CourseImage.register(course, "https://example.com/thumb.jpg"));
        saveWishlist(course);

        WishlistCourseItem item = courseWishlistRepository
                .findWishlistCourses(student.getId(), PageRequest.of(0, 10))
                .getContent().get(0);

        assertThat(item.thumbnail()).isEqualTo("https://example.com/thumb.jpg");
    }

    @Test
    @DisplayName("코스 이미지가 여러 개이면 가장 먼저 등록된 이미지(id 최소값)를 thumbnail로 반환")
    void findWishlistCourses_thumbnail_first_image_when_multiple_images() {
        Course course = saveCourse("이미지 여러 개인 코스");
        CourseImage firstImage = CourseImage.register(course, "https://example.com/first-thumb.jpg");
        CourseImage secondImage = CourseImage.register(course, "https://example.com/second-thumb.jpg");
        courseImageRepository.save(firstImage);
        courseImageRepository.save(secondImage);
        saveWishlist(course);

        WishlistCourseItem item = courseWishlistRepository
                .findWishlistCourses(student.getId(), PageRequest.of(0, 10))
                .getContent().get(0);

        assertThat(item.thumbnail()).isEqualTo("https://example.com/first-thumb.jpg");
    }

    // ────────────────────────────────────────────────────────────
    // 페이징 검증
    // ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("찜 3건, size=2 → 첫 페이지 2건, 전체 3건")
    void findWishlistCourses_paging_first_page() {
        saveWishlist(saveCourse("코스A"));
        saveWishlist(saveCourse("코스B"));
        saveWishlist(saveCourse("코스C"));

        Page<WishlistCourseItem> result =
                courseWishlistRepository.findWishlistCourses(student.getId(), PageRequest.of(0, 2));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.isFirst()).isTrue();
        assertThat(result.isLast()).isFalse();
    }

    @Test
    @DisplayName("찜 3건, size=2 → 두 번째 페이지 1건")
    void findWishlistCourses_paging_second_page() {
        saveWishlist(saveCourse("코스A"));
        saveWishlist(saveCourse("코스B"));
        saveWishlist(saveCourse("코스C"));

        Page<WishlistCourseItem> result =
                courseWishlistRepository.findWishlistCourses(student.getId(), PageRequest.of(1, 2));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.isLast()).isTrue();
    }

    // ────────────────────────────────────────────────────────────
    // 헬퍼 메서드
    // ────────────────────────────────────────────────────────────

    private Course saveCourse(String title) {
        LocalDateTime now = LocalDateTime.now();
        return courseRepository.save(Course.register(
                category.getId(),
                instructor.getId(),
                title,
                "코스 설명",
                "서울시 강남구",
                "상세 주소 101호",
                10,
                BigInteger.valueOf(50000),
                CourseLevel.BEGINNER,
                now.plusDays(1),
                now.plusDays(3),
                now.plusDays(4),
                now.plusDays(5)
        ));
    }

    private void saveWishlist(Course course) {
        courseWishlistRepository.save(CourseWishlist.register(student.getId(), course.getId()));
    }
}
