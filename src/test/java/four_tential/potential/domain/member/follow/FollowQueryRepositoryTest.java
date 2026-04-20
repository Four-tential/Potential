package four_tential.potential.domain.member.follow;

import four_tential.potential.domain.course.course.Course;
import four_tential.potential.domain.course.course.CourseLevel;
import four_tential.potential.domain.course.course.CourseRepository;
import four_tential.potential.domain.course.course_category.CourseCategory;
import four_tential.potential.domain.course.course_category.CourseCategoryRepository;
import four_tential.potential.domain.member.instructor_member.InstructorMember;
import four_tential.potential.domain.member.instructor_member.InstructorMemberRepository;
import four_tential.potential.domain.member.member.Member;
import four_tential.potential.domain.member.member.MemberRepository;
import four_tential.potential.domain.review.review.Review;
import four_tential.potential.domain.review.review.ReviewRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@Transactional
class FollowQueryRepositoryTest extends RedisTestContainer {

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private InstructorMemberRepository instructorMemberRepository;

    @Autowired
    private CourseCategoryRepository courseCategoryRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    private Member follower;
    private CourseCategory fitnessCategory;

    @BeforeEach
    void setUp() {
        follower = memberRepository.save(
                Member.register("follower@test.com", "encodedPwd!", "팔로워회원", "010-1111-0000")
        );
        fitnessCategory = courseCategoryRepository.save(CourseCategory.register("FITNESS_TEST", "피트니스테스트"));
    }

    // 팔로우 목록 조회

    @Test
    @DisplayName("팔로우한 강사 3명 조회 - content 3건 반환")
    void findFollowedInstructors_three_instructors() {
        saveFollowedInstructor("ins1@test.com", "강사A");
        saveFollowedInstructor("ins2@test.com", "강사B");
        saveFollowedInstructor("ins3@test.com", "강사C");

        Page<FollowQueryResult> result =
                followRepository.findFollowedInstructors(follower.getId(), PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("팔로우한 강사가 없으면 빈 페이지 반환")
    void findFollowedInstructors_empty() {
        Page<FollowQueryResult> result =
                followRepository.findFollowedInstructors(follower.getId(), PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("다른 수강생의 팔로우는 내 목록에 포함되지 않음")
    void findFollowedInstructors_excludesOtherFollowers() {
        Member otherFollower = memberRepository.save(
                Member.register("other@test.com", "encodedPwd!", "다른수강생", "010-9999-0000")
        );
        InstructorMember instructor = approvedInstructor("ins1@test.com", "강사A");
        followRepository.save(Follow.register(otherFollower.getId(), instructor.getId()));

        Page<FollowQueryResult> result =
                followRepository.findFollowedInstructors(follower.getId(), PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
    }

    // courseCount 검증

    @Test
    @DisplayName("코스 없는 강사 팔로우 시 courseCount = 0")
    void findFollowedInstructors_courseCount_zero_when_no_courses() {
        saveFollowedInstructor("ins1@test.com", "강사A");

        FollowQueryResult item = followRepository
                .findFollowedInstructors(follower.getId(), PageRequest.of(0, 10))
                .getContent().get(0);

        assertThat(item.courseCount()).isZero();
    }

    @Test
    @DisplayName("코스 2개인 강사 팔로우 시 courseCount = 2")
    void findFollowedInstructors_courseCount_two() {
        InstructorMember instructor = approvedInstructor("ins1@test.com", "강사A");
        followRepository.save(Follow.register(follower.getId(), instructor.getId()));
        saveCourse(instructor.getId());
        saveCourse(instructor.getId());

        FollowQueryResult item = followRepository
                .findFollowedInstructors(follower.getId(), PageRequest.of(0, 10))
                .getContent().get(0);

        assertThat(item.courseCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("여러 강사가 있을 때 각 강사의 courseCount가 독립적으로 계산됨")
    void findFollowedInstructors_courseCount_independent_per_instructor() {
        InstructorMember ins1 = approvedInstructor("ins1@test.com", "강사A");
        InstructorMember ins2 = approvedInstructor("ins2@test.com", "강사B");
        followRepository.save(Follow.register(follower.getId(), ins1.getId()));
        followRepository.save(Follow.register(follower.getId(), ins2.getId()));

        saveCourse(ins1.getId());
        saveCourse(ins1.getId());
        saveCourse(ins1.getId()); // ins1 = 3개

        // ins2 = 0개

        Page<FollowQueryResult> result =
                followRepository.findFollowedInstructors(follower.getId(), PageRequest.of(0, 10));

        FollowQueryResult itemA = result.getContent().stream()
                .filter(i -> i.name().equals("강사A")).findFirst().orElseThrow();
        FollowQueryResult itemB = result.getContent().stream()
                .filter(i -> i.name().equals("강사B")).findFirst().orElseThrow();

        assertThat(itemA.courseCount()).isEqualTo(3L);
        assertThat(itemB.courseCount()).isZero();
    }

    // averageRating 검증

    @Test
    @DisplayName("리뷰 없는 강사 팔로우 시 averageRating = null")
    void findFollowedInstructors_averageRating_null_when_no_reviews() {
        InstructorMember instructor = approvedInstructor("ins1@test.com", "강사A");
        followRepository.save(Follow.register(follower.getId(), instructor.getId()));
        saveCourse(instructor.getId());

        FollowQueryResult item = followRepository
                .findFollowedInstructors(follower.getId(), PageRequest.of(0, 10))
                .getContent().get(0);

        assertThat(item.averageRating()).isNull();
    }

    @Test
    @DisplayName("리뷰 2개(4점, 5점)면 averageRating = 4.5")
    void findFollowedInstructors_averageRating_4_5() {
        InstructorMember instructor = approvedInstructor("ins1@test.com", "강사A");
        followRepository.save(Follow.register(follower.getId(), instructor.getId()));
        Course course = saveCourse(instructor.getId());

        UUID orderId = UUID.randomUUID();
        reviewRepository.save(Review.register(follower.getId(), course.getId(), orderId, 4, "좋아요"));
        reviewRepository.save(Review.register(follower.getId(), course.getId(), orderId, 5, "최고에요"));

        FollowQueryResult item = followRepository
                .findFollowedInstructors(follower.getId(), PageRequest.of(0, 10))
                .getContent().get(0);

        assertThat(item.averageRating()).isCloseTo(4.5, within(0.01));
    }

    @Test
    @DisplayName("코스가 없으면 리뷰도 없으므로 averageRating = null")
    void findFollowedInstructors_averageRating_null_when_no_courses() {
        saveFollowedInstructor("ins1@test.com", "강사A");

        FollowQueryResult item = followRepository
                .findFollowedInstructors(follower.getId(), PageRequest.of(0, 10))
                .getContent().get(0);

        assertThat(item.averageRating()).isNull();
    }

    // 페이징 검증

    @Test
    @DisplayName("팔로우 3건, 페이지 size=2 → 첫 페이지 2건, 전체 3건")
    void findFollowedInstructors_paging_first_page() {
        saveFollowedInstructor("ins1@test.com", "강사A");
        saveFollowedInstructor("ins2@test.com", "강사B");
        saveFollowedInstructor("ins3@test.com", "강사C");

        Page<FollowQueryResult> result =
                followRepository.findFollowedInstructors(follower.getId(), PageRequest.of(0, 2));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.isFirst()).isTrue();
        assertThat(result.isLast()).isFalse();
    }

    @Test
    @DisplayName("팔로우 3건, 페이지 size=2 → 두 번째 페이지 1건")
    void findFollowedInstructors_paging_second_page() {
        saveFollowedInstructor("ins1@test.com", "강사A");
        saveFollowedInstructor("ins2@test.com", "강사B");
        saveFollowedInstructor("ins3@test.com", "강사C");

        Page<FollowQueryResult> result =
                followRepository.findFollowedInstructors(follower.getId(), PageRequest.of(1, 2));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.isLast()).isTrue();
    }

    // 응답 필드 검증

    @Test
    @DisplayName("응답 항목에 memberId, name, categoryCode, categoryName, followedAt 이 올바르게 매핑됨")
    void findFollowedInstructors_response_fields_mapped_correctly() {
        InstructorMember instructor = approvedInstructor("ins1@test.com", "강사이름");
        followRepository.save(Follow.register(follower.getId(), instructor.getId()));

        // instructorMember의 memberId(member 테이블의 PK)를 가져옴
        Member instructorMember = memberRepository.findById(instructor.getMemberId()).orElseThrow();

        FollowQueryResult item = followRepository
                .findFollowedInstructors(follower.getId(), PageRequest.of(0, 10))
                .getContent().get(0);

        assertThat(item.memberId()).isEqualTo(instructorMember.getId());
        assertThat(item.name()).isEqualTo("강사이름");
        assertThat(item.categoryCode()).isEqualTo("FITNESS_TEST");
        assertThat(item.categoryName()).isEqualTo("피트니스테스트");
        assertThat(item.followedAt()).isNotNull();
    }

    @Test
    @DisplayName("프로필 이미지가 없는 강사는 profileImageUrl = null")
    void findFollowedInstructors_profileImageUrl_null_when_not_set() {
        saveFollowedInstructor("ins1@test.com", "강사A");

        FollowQueryResult item = followRepository
                .findFollowedInstructors(follower.getId(), PageRequest.of(0, 10))
                .getContent().get(0);

        assertThat(item.profileImageUrl()).isNull();
    }

    // 헬퍼 메서드

    private InstructorMember approvedInstructor(String email, String name) {
        Member member = memberRepository.save(
                Member.register(email, "encodedPwd!", name, "010-0000-0000")
        );
        InstructorMember im = InstructorMember.register(
                member.getId(), "FITNESS_TEST", name + " 소개", "https://img.url/cert.jpg"
        );
        im.approve();
        return instructorMemberRepository.save(im);
    }

    private void saveFollowedInstructor(String email, String name) {
        InstructorMember instructor = approvedInstructor(email, name);
        followRepository.save(Follow.register(follower.getId(), instructor.getId()));
    }

    private Course saveCourse(UUID instructorMemberId) {
        LocalDateTime now = LocalDateTime.now();
        return courseRepository.save(Course.register(
                fitnessCategory.getId(),
                instructorMemberId,
                "테스트 코스",
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
}
