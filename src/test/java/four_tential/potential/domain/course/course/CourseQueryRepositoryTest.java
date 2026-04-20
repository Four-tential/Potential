package four_tential.potential.domain.course.course;

import four_tential.potential.domain.course.course_category.CourseCategory;
import four_tential.potential.domain.course.course_category.CourseCategoryRepository;
import four_tential.potential.domain.course.course_image.CourseImage;
import four_tential.potential.domain.course.course_image.CourseImageRepository;
import four_tential.potential.domain.member.instructor_member.InstructorMember;
import four_tential.potential.domain.member.instructor_member.InstructorMemberRepository;
import four_tential.potential.domain.member.member.Member;
import four_tential.potential.domain.member.member.MemberRepository;
import four_tential.potential.infra.redis.RedisTestContainer;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class CourseQueryRepositoryTest extends RedisTestContainer {

    @Autowired private CourseRepository courseRepository;
    @Autowired private CourseImageRepository courseImageRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private InstructorMemberRepository instructorMemberRepository;
    @Autowired private CourseCategoryRepository courseCategoryRepository;

    private InstructorMember instructor;
    private CourseCategory category;

    @BeforeEach
    void setUp() {
        Member instructorMember = memberRepository.save(
                Member.register("instructor@test.com", "encodedPwd!", "테스트강사", "010-1111-0000")
        );
        category = courseCategoryRepository.save(CourseCategory.register("TEST_CAT", "테스트카테고리"));
        InstructorMember im = InstructorMember.register(
                instructorMember.getId(), "TEST_CAT", "강사 소개", "https://img.url/cert.jpg"
        );
        im.approve();
        instructor = instructorMemberRepository.save(im);
    }

    @Test
    @DisplayName("PREPARATION 상태 코스는 항상 결과에서 제외된다")
    void findCourses_excludes_preparation_courses() {
        savePreparationCourse("승인 대기 코스");
        saveOpenCourse("공개 코스");

        Page<CourseListQueryResult> result = courseRepository.findCourses(emptyCondition(), PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).title()).isEqualTo("공개 코스");
        assertThat(result.getContent().get(0).status()).isNotEqualTo(CourseStatus.PREPARATION);
    }

    @Test
    @DisplayName("PREPARATION 코스만 있으면 빈 결과를 반환한다")
    void findCourses_only_preparation_courses_returns_empty() {
        savePreparationCourse("승인 대기 코스 A");
        savePreparationCourse("승인 대기 코스 B");

        Page<CourseListQueryResult> result = courseRepository.findCourses(emptyCondition(), PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("categoryCode 필터 - 해당 카테고리 코스만 반환된다")
    void findCourses_filter_by_categoryCode() {
        CourseCategory otherCategory = courseCategoryRepository.save(
                CourseCategory.register("OTHER_CAT", "다른카테고리")
        );
        saveOpenCourse("테스트카테고리 코스");
        saveOpenCourseWithCategory("다른카테고리 코스", otherCategory);

        CourseSearchCondition condition = new CourseSearchCondition("TEST_CAT", null, null, null, null, null, null);
        Page<CourseListQueryResult> result = courseRepository.findCourses(condition, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).categoryCode()).isEqualTo("TEST_CAT");
    }

    @Test
    @DisplayName("categoryCode 필터 - 일치하는 카테고리가 없으면 빈 결과 반환")
    void findCourses_filter_by_categoryCode_no_match() {
        saveOpenCourse("코스 A");

        CourseSearchCondition condition = new CourseSearchCondition("NO_SUCH_CAT", null, null, null, null, null, null);
        Page<CourseListQueryResult> result = courseRepository.findCourses(condition, PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("status 필터 - OPEN 코스만 반환된다")
    void findCourses_filter_by_status_open() {
        saveOpenCourse("OPEN 코스");
        saveClosedCourse("CLOSED 코스");

        CourseSearchCondition condition = new CourseSearchCondition(null, CourseStatus.OPEN, null, null, null, null, null);
        Page<CourseListQueryResult> result = courseRepository.findCourses(condition, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).status()).isEqualTo(CourseStatus.OPEN);
    }

    @Test
    @DisplayName("status 필터 - CLOSED 코스만 반환된다")
    void findCourses_filter_by_status_closed() {
        saveOpenCourse("OPEN 코스");
        saveClosedCourse("CLOSED 코스");

        CourseSearchCondition condition = new CourseSearchCondition(null, CourseStatus.CLOSED, null, null, null, null, null);
        Page<CourseListQueryResult> result = courseRepository.findCourses(condition, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).status()).isEqualTo(CourseStatus.CLOSED);
    }

    @Test
    @DisplayName("level 필터 - BEGINNER 코스만 반환된다")
    void findCourses_filter_by_level_beginner() {
        saveOpenCourseWithLevel("입문 코스", CourseLevel.BEGINNER);
        saveOpenCourseWithLevel("중급 코스", CourseLevel.INTERMEDIATE);

        CourseSearchCondition condition = new CourseSearchCondition(null, null, CourseLevel.BEGINNER, null, null, null, null);
        Page<CourseListQueryResult> result = courseRepository.findCourses(condition, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).level()).isEqualTo(CourseLevel.BEGINNER);
    }

    @Test
    @DisplayName("level 필터 - 일치하는 난이도가 없으면 빈 결과 반환")
    void findCourses_filter_by_level_no_match() {
        saveOpenCourseWithLevel("입문 코스", CourseLevel.BEGINNER);

        CourseSearchCondition condition = new CourseSearchCondition(null, null, CourseLevel.ADVANCE, null, null, null, null);
        Page<CourseListQueryResult> result = courseRepository.findCourses(condition, PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("keyword 필터 - 제목에 키워드가 포함된 코스만 반환된다")
    void findCourses_filter_by_keyword() {
        saveOpenCourse("필라테스 입문반");
        saveOpenCourse("요가 기초반");

        CourseSearchCondition req = new CourseSearchCondition(null, null, null, "필라테스", null, null, null);
        Page<CourseListQueryResult> result = courseRepository.findCourses(req, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).title()).isEqualTo("필라테스 입문반");
    }

    @Test
    @DisplayName("keyword 필터 - 대소문자 구분 없이 검색된다")
    void findCourses_filter_by_keyword_case_insensitive() {
        saveOpenCourse("Spring Boot 입문");

        CourseSearchCondition req = new CourseSearchCondition(null, null, null, "spring boot", null, null, null);
        Page<CourseListQueryResult> result = courseRepository.findCourses(req, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("keyword 필터 - 일치하는 키워드가 없으면 빈 결과 반환")
    void findCourses_filter_by_keyword_no_match() {
        saveOpenCourse("필라테스 입문반");

        CourseSearchCondition req = new CourseSearchCondition(null, null, null, "요가", null, null, null);
        Page<CourseListQueryResult> result = courseRepository.findCourses(req, PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("minPrice 필터 - 최소 가격 이상 코스만 반환된다")
    void findCourses_filter_by_minPrice() {
        saveOpenCourseWithPrice("저렴한 코스", BigInteger.valueOf(30000));
        saveOpenCourseWithPrice("비싼 코스", BigInteger.valueOf(80000));

        CourseSearchCondition req = new CourseSearchCondition(null, null, null, null, BigInteger.valueOf(50000), null, null);
        Page<CourseListQueryResult> result = courseRepository.findCourses(req, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).price()).isEqualByComparingTo(BigInteger.valueOf(80000));
    }

    @Test
    @DisplayName("maxPrice 필터 - 최대 가격 이하 코스만 반환된다")
    void findCourses_filter_by_maxPrice() {
        saveOpenCourseWithPrice("저렴한 코스", BigInteger.valueOf(30000));
        saveOpenCourseWithPrice("비싼 코스", BigInteger.valueOf(80000));

        CourseSearchCondition req = new CourseSearchCondition(null, null, null, null, null, BigInteger.valueOf(50000), null);
        Page<CourseListQueryResult> result = courseRepository.findCourses(req, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).price()).isEqualByComparingTo(BigInteger.valueOf(30000));
    }

    @Test
    @DisplayName("minPrice·maxPrice 동시 적용 - 범위 내 코스만 반환된다")
    void findCourses_filter_by_price_range() {
        saveOpenCourseWithPrice("2만원 코스", BigInteger.valueOf(20000));
        saveOpenCourseWithPrice("5만원 코스", BigInteger.valueOf(50000));
        saveOpenCourseWithPrice("10만원 코스", BigInteger.valueOf(100000));

        CourseSearchCondition req = new CourseSearchCondition(
                null, null, null, null, BigInteger.valueOf(30000), BigInteger.valueOf(70000), null
        );
        Page<CourseListQueryResult> result = courseRepository.findCourses(req, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).price()).isEqualByComparingTo(BigInteger.valueOf(50000));
    }

    @Test
    @DisplayName("PRICE_ASC 정렬 - 가격 오름차순으로 반환된다")
    void findCourses_sort_by_price_asc() {
        saveOpenCourseWithPrice("고가 코스", BigInteger.valueOf(100000));
        saveOpenCourseWithPrice("저가 코스", BigInteger.valueOf(10000));
        saveOpenCourseWithPrice("중가 코스", BigInteger.valueOf(50000));

        CourseSearchCondition req = new CourseSearchCondition(null, null, null, null, null, null, CourseSort.PRICE_ASC);
        Page<CourseListQueryResult> result = courseRepository.findCourses(req, PageRequest.of(0, 10));

        List<BigInteger> prices = result.getContent().stream()
                .map(CourseListQueryResult::price)
                .toList();
        assertThat(prices).isSortedAccordingTo(BigInteger::compareTo);
        assertThat(prices.get(0)).isEqualByComparingTo(BigInteger.valueOf(10000));
        assertThat(prices.get(2)).isEqualByComparingTo(BigInteger.valueOf(100000));
    }

    @Test
    @DisplayName("PRICE_DESC 정렬 - 가격 내림차순으로 반환된다")
    void findCourses_sort_by_price_desc() {
        saveOpenCourseWithPrice("고가 코스", BigInteger.valueOf(100000));
        saveOpenCourseWithPrice("저가 코스", BigInteger.valueOf(10000));
        saveOpenCourseWithPrice("중가 코스", BigInteger.valueOf(50000));

        CourseSearchCondition req = new CourseSearchCondition(null, null, null, null, null, null, CourseSort.PRICE_DESC);
        Page<CourseListQueryResult> result = courseRepository.findCourses(req, PageRequest.of(0, 10));

        List<BigInteger> prices = result.getContent().stream()
                .map(CourseListQueryResult::price)
                .toList();
        assertThat(prices.get(0)).isEqualByComparingTo(BigInteger.valueOf(100000));
        assertThat(prices.get(2)).isEqualByComparingTo(BigInteger.valueOf(10000));
    }

    @Test
    @DisplayName("LATEST 정렬(기본값) - 코스 3건이 모두 반환된다")
    void findCourses_sort_latest_returns_all() {
        saveOpenCourse("코스 A");
        saveOpenCourse("코스 B");
        saveOpenCourse("코스 C");

        CourseSearchCondition req = new CourseSearchCondition(null, null, null, null, null, null, CourseSort.LATEST);
        Page<CourseListQueryResult> result = courseRepository.findCourses(req, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(3);
    }

    @Test
    @DisplayName("코스 이미지가 없으면 thumbnailUrl = null")
    void findCourses_thumbnail_null_when_no_images() {
        saveOpenCourse("이미지 없는 코스");

        Page<CourseListQueryResult> result = courseRepository.findCourses(emptyCondition(), PageRequest.of(0, 10));

        assertThat(result.getContent().get(0).thumbnailUrl()).isNull();
    }

    @Test
    @DisplayName("코스 이미지가 1개이면 해당 URL이 thumbnailUrl로 반환된다")
    void findCourses_thumbnail_returned_when_single_image() {
        Course course = saveOpenCourse("이미지 있는 코스");
        courseImageRepository.save(CourseImage.register(course, "https://example.com/thumb.jpg"));

        Page<CourseListQueryResult> result = courseRepository.findCourses(emptyCondition(), PageRequest.of(0, 10));

        assertThat(result.getContent().get(0).thumbnailUrl()).isEqualTo("https://example.com/thumb.jpg");
    }

    @Test
    @DisplayName("코스 이미지가 여러 개이면 가장 먼저 등록된 이미지(id 최솟값)가 thumbnailUrl로 반환된다")
    void findCourses_thumbnail_first_image_when_multiple_images() {
        Course course = saveOpenCourse("이미지 여러 개 코스");
        courseImageRepository.save(CourseImage.register(course, "https://example.com/first.jpg"));
        courseImageRepository.save(CourseImage.register(course, "https://example.com/second.jpg"));

        Page<CourseListQueryResult> result = courseRepository.findCourses(emptyCondition(), PageRequest.of(0, 10));

        assertThat(result.getContent().get(0).thumbnailUrl()).isEqualTo("https://example.com/first.jpg");
    }

    @Test
    @DisplayName("응답 DTO에 courseId, title, categoryCode, categoryName, instructorName, price, level, status 등이 올바르게 매핑된다")
    void findCourses_response_fields_mapped_correctly() {
        saveOpenCourseWithLevel("소도구 필라테스", CourseLevel.BEGINNER);

        Page<CourseListQueryResult> result = courseRepository.findCourses(emptyCondition(), PageRequest.of(0, 10));
        CourseListQueryResult item = result.getContent().get(0);

        assertThat(item.title()).isEqualTo("소도구 필라테스");
        assertThat(item.categoryCode()).isEqualTo("TEST_CAT");
        assertThat(item.categoryName()).isEqualTo("테스트카테고리");
        assertThat(item.instructorName()).isEqualTo("테스트강사");
        assertThat(item.level()).isEqualTo(CourseLevel.BEGINNER);
        assertThat(item.status()).isEqualTo(CourseStatus.OPEN);
        assertThat(item.courseId()).isNotNull();
        assertThat(item.instructorMemberId()).isNotNull();
        assertThat(item.orderOpenAt()).isNotNull();
        assertThat(item.startAt()).isNotNull();
    }

    @Test
    @DisplayName("페이징 - size=2일 때 첫 페이지는 2건, totalElements는 전체 건수")
    void findCourses_paging_first_page() {
        saveOpenCourse("코스 A");
        saveOpenCourse("코스 B");
        saveOpenCourse("코스 C");

        Page<CourseListQueryResult> result = courseRepository.findCourses(emptyCondition(), PageRequest.of(0, 2));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.isFirst()).isTrue();
        assertThat(result.isLast()).isFalse();
    }

    @Test
    @DisplayName("페이징 - size=2일 때 두 번째 페이지는 1건, isLast=true")
    void findCourses_paging_second_page() {
        saveOpenCourse("코스 A");
        saveOpenCourse("코스 B");
        saveOpenCourse("코스 C");

        Page<CourseListQueryResult> result = courseRepository.findCourses(emptyCondition(), PageRequest.of(1, 2));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.isLast()).isTrue();
    }

    @Test
    @DisplayName("조건에 맞는 코스가 없으면 빈 페이지를 반환한다")
    void findCourses_no_matching_courses_returns_empty_page() {
        Page<CourseListQueryResult> result = courseRepository.findCourses(emptyCondition(), PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("복합 조건 - categoryCode + level + maxPrice 동시 적용 시 모두 만족하는 코스만 반환된다")
    void findCourses_multiple_filters_combined() {
        CourseCategory otherCategory = courseCategoryRepository.save(
                CourseCategory.register("OTHER_CAT", "다른카테고리")
        );
        saveCourse("대상 코스", category, CourseLevel.BEGINNER, BigInteger.valueOf(50000), true);
        saveCourse("다른카테고리 코스", otherCategory, CourseLevel.BEGINNER, BigInteger.valueOf(50000), true);
        saveCourse("중급 코스", category, CourseLevel.INTERMEDIATE, BigInteger.valueOf(50000), true);
        saveCourse("고가 코스", category, CourseLevel.BEGINNER, BigInteger.valueOf(100000), true);

        CourseSearchCondition req = new CourseSearchCondition(
                "TEST_CAT", null, CourseLevel.BEGINNER, null, null, BigInteger.valueOf(70000), null
        );
        Page<CourseListQueryResult> result = courseRepository.findCourses(req, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).title()).isEqualTo("대상 코스");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // findCoursesByInstructorMemberId
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("강사의 코스 목록 - PREPARATION 코스는 제외된다")
    void findCoursesByInstructorMemberId_excludes_preparation_courses() {
        savePreparationCourse("승인 대기 코스");
        saveOpenCourse("공개 코스");

        Page<InstructorCourseQueryResult> result = courseRepository
                .findCoursesByInstructorMemberId(instructor.getId(), PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).title()).isEqualTo("공개 코스");
    }

    @Test
    @DisplayName("강사의 코스 목록 - 다른 강사의 코스는 포함되지 않는다")
    void findCoursesByInstructorMemberId_excludes_other_instructors_courses() {
        Member otherMember = memberRepository.save(
                Member.register("other@test.com", "encodedPwd!", "다른강사", "010-2222-0000")
        );
        InstructorMember otherInstructor = InstructorMember.register(
                otherMember.getId(), "TEST_CAT", "다른 강사 소개", "https://img.url/other.jpg"
        );
        otherInstructor.approve();
        otherInstructor = instructorMemberRepository.save(otherInstructor);

        Course myCourse = buildCourse("내 코스", category, CourseLevel.BEGINNER, BigInteger.valueOf(50000));
        myCourse.open();
        courseRepository.save(myCourse);

        Course otherCourse = Course.register(
                category.getId(), otherInstructor.getId(), "다른 강사 코스", "설명",
                "서울", "주소", 20, BigInteger.valueOf(50000), CourseLevel.BEGINNER,
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(3),
                LocalDateTime.now().plusDays(4), LocalDateTime.now().plusDays(5)
        );
        otherCourse.open();
        courseRepository.save(otherCourse);

        Page<InstructorCourseQueryResult> result = courseRepository
                .findCoursesByInstructorMemberId(instructor.getId(), PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).title()).isEqualTo("내 코스");
    }

    @Test
    @DisplayName("강사의 코스 목록 - 코스가 없으면 빈 페이지를 반환한다")
    void findCoursesByInstructorMemberId_returns_empty_when_no_courses() {
        Page<InstructorCourseQueryResult> result = courseRepository
                .findCoursesByInstructorMemberId(instructor.getId(), PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("강사의 코스 목록 - 응답 필드가 올바르게 매핑된다")
    void findCoursesByInstructorMemberId_fields_mapped_correctly() {
        saveOpenCourseWithLevel("매핑 확인 코스", CourseLevel.INTERMEDIATE);

        Page<InstructorCourseQueryResult> result = courseRepository
                .findCoursesByInstructorMemberId(instructor.getId(), PageRequest.of(0, 10));
        InstructorCourseQueryResult item = result.getContent().get(0);

        assertThat(item.courseId()).isNotNull();
        assertThat(item.title()).isEqualTo("매핑 확인 코스");
        assertThat(item.level()).isEqualTo(CourseLevel.INTERMEDIATE);
        assertThat(item.status()).isEqualTo(CourseStatus.OPEN);
        assertThat(item.capacity()).isEqualTo(20);
        assertThat(item.price()).isEqualByComparingTo(BigInteger.valueOf(50000));
        assertThat(item.orderOpenAt()).isNotNull();
        assertThat(item.startAt()).isNotNull();
    }

    @Test
    @DisplayName("강사의 코스 목록 - 페이징이 올바르게 동작한다")
    void findCoursesByInstructorMemberId_paging_works() {
        saveOpenCourse("코스 A");
        saveOpenCourse("코스 B");
        saveOpenCourse("코스 C");

        Page<InstructorCourseQueryResult> result = courseRepository
                .findCoursesByInstructorMemberId(instructor.getId(), PageRequest.of(0, 2));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.isFirst()).isTrue();
        assertThat(result.isLast()).isFalse();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // findMyCoursesByInstructorMemberId (강사 본인 전용 — PREPARATION 포함)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("내 코스 목록 - PREPARATION 코스가 포함된다")
    void findMyCoursesByInstructorMemberId_includes_preparation_courses() {
        savePreparationCourse("승인 대기 코스");
        saveOpenCourse("공개 코스");

        Page<InstructorCourseQueryResult> result = courseRepository
                .findMyCoursesByInstructorMemberId(instructor.getId(), PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().stream().map(InstructorCourseQueryResult::title).toList())
                .containsExactlyInAnyOrder("승인 대기 코스", "공개 코스");
    }

    @Test
    @DisplayName("내 코스 목록 - PREPARATION/OPEN/CLOSED 모두 포함된다")
    void findMyCoursesByInstructorMemberId_includes_all_statuses() {
        savePreparationCourse("승인 대기 코스");
        saveOpenCourse("공개 코스");
        saveClosedCourse("종료 코스");

        Page<InstructorCourseQueryResult> result = courseRepository
                .findMyCoursesByInstructorMemberId(instructor.getId(), PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent().stream().map(InstructorCourseQueryResult::status).toList())
                .containsExactlyInAnyOrder(CourseStatus.PREPARATION, CourseStatus.OPEN, CourseStatus.CLOSED);
    }

    @Test
    @DisplayName("내 코스 목록 - 다른 강사의 코스는 포함되지 않는다")
    void findMyCoursesByInstructorMemberId_excludes_other_instructors_courses() {
        Member otherMember = memberRepository.save(
                Member.register("other2@test.com", "encodedPwd!", "다른강사2", "010-3333-0000")
        );
        InstructorMember otherInstructor = InstructorMember.register(
                otherMember.getId(), "TEST_CAT", "다른 강사 소개", "https://img.url/other.jpg"
        );
        otherInstructor.approve();
        otherInstructor = instructorMemberRepository.save(otherInstructor);

        saveOpenCourse("내 코스");
        Course otherCourse = Course.register(
                category.getId(), otherInstructor.getId(), "다른 강사 코스", "설명",
                "서울", "주소", 20, BigInteger.valueOf(50000), CourseLevel.BEGINNER,
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(3),
                LocalDateTime.now().plusDays(4), LocalDateTime.now().plusDays(5)
        );
        courseRepository.save(otherCourse);

        Page<InstructorCourseQueryResult> result = courseRepository
                .findMyCoursesByInstructorMemberId(instructor.getId(), PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).title()).isEqualTo("내 코스");
    }

    @Test
    @DisplayName("내 코스 목록 - 코스가 없으면 빈 페이지를 반환한다")
    void findMyCoursesByInstructorMemberId_returns_empty_when_no_courses() {
        Page<InstructorCourseQueryResult> result = courseRepository
                .findMyCoursesByInstructorMemberId(instructor.getId(), PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("내 코스 목록 - 페이징이 올바르게 동작한다")
    void findMyCoursesByInstructorMemberId_paging_works() {
        savePreparationCourse("코스 A");
        saveOpenCourse("코스 B");
        saveOpenCourse("코스 C");

        Page<InstructorCourseQueryResult> result = courseRepository
                .findMyCoursesByInstructorMemberId(instructor.getId(), PageRequest.of(0, 2));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.isFirst()).isTrue();
        assertThat(result.isLast()).isFalse();
    }

    private CourseSearchCondition emptyCondition() {
        return new CourseSearchCondition(null, null, null, null, null, null, null);
    }

    private Course saveOpenCourse(String title) {
        Course course = buildCourse(title, category, CourseLevel.BEGINNER, BigInteger.valueOf(50000));
        course.open();
        return courseRepository.save(course);
    }

    private Course saveOpenCourseWithPrice(String title, BigInteger price) {
        Course course = buildCourse(title, category, CourseLevel.BEGINNER, price);
        course.open();
        return courseRepository.save(course);
    }

    private Course saveOpenCourseWithLevel(String title, CourseLevel level) {
        Course course = buildCourse(title, category, level, BigInteger.valueOf(50000));
        course.open();
        return courseRepository.save(course);
    }

    private Course saveOpenCourseWithCategory(String title, CourseCategory cat) {
        Course course = buildCourse(title, cat, CourseLevel.BEGINNER, BigInteger.valueOf(50000));
        course.open();
        return courseRepository.save(course);
    }

    private Course savePreparationCourse(String title) {
        return courseRepository.save(buildCourse(title, category, CourseLevel.BEGINNER, BigInteger.valueOf(50000)));
    }

    private Course saveClosedCourse(String title) {
        Course course = buildCourse(title, category, CourseLevel.BEGINNER, BigInteger.valueOf(50000));
        course.open();
        course.close();
        return courseRepository.save(course);
    }

    private Course saveCourse(String title, CourseCategory cat, CourseLevel level, BigInteger price, boolean open) {
        Course course = buildCourse(title, cat, level, price);
        if (open) course.open();
        return courseRepository.save(course);
    }

    private Course buildCourse(String title, CourseCategory cat, CourseLevel level, BigInteger price) {
        LocalDateTime now = LocalDateTime.now();
        return Course.register(
                cat.getId(),
                instructor.getId(),
                title,
                "코스 설명",
                "서울특별시 강남구",
                "테헤란로 123",
                20,
                price,
                level,
                now.plusDays(1),
                now.plusDays(3),
                now.plusDays(4),
                now.plusDays(5)
        );
    }
}
