package four_tential.potential.domain.member.instructor_member;

import four_tential.potential.domain.course.course_category.CourseCategory;
import four_tential.potential.domain.course.course_category.CourseCategoryRepository;
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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class InstructorMemberQueryRepositoryTest extends RedisTestContainer {

    @Autowired
    private InstructorMemberRepository instructorMemberRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CourseCategoryRepository courseCategoryRepository;

    private Member savedMember;
    private InstructorMember savedInstructorMember;

    @BeforeEach
    void setUp() {
        savedMember = memberRepository.save(
                Member.register("instructor@test.com", "encodedPassword123!", "홍길동", "010-1234-5678")
        );
        courseCategoryRepository.save(CourseCategory.register("FITNESS", "피트니스"));
        savedInstructorMember = instructorMemberRepository.save(
                InstructorMember.register(
                        savedMember.getId(),
                        "FITNESS",
                        "10년 경력의 피트니스 강사입니다",
                        "https://example.com/cert.jpg"
                )
        );
    }

    // region findInstructorApplications
    @Test
    @DisplayName("findInstructorApplications - status 필터 없이 전체 목록 조회")
    void findInstructorApplications_noFilter_returnsAll() {
        Page<InstructorApplicationItemResult> result =
                instructorMemberRepository.findInstructorApplications(null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);

        InstructorApplicationItemResult item = result.getContent().get(0);
        assertThat(item.memberId()).isEqualTo(savedMember.getId());
        assertThat(item.memberName()).isEqualTo("홍길동");
        assertThat(item.categoryCode()).isEqualTo("FITNESS");
        assertThat(item.categoryName()).isEqualTo("피트니스");
        assertThat(item.status()).isEqualTo(InstructorMemberStatus.PENDING);
    }

    @Test
    @DisplayName("findInstructorApplications - PENDING 필터 적용 시 PENDING 목록만 반환")
    void findInstructorApplications_pendingFilter_returnsPendingOnly() {
        Page<InstructorApplicationItemResult> result =
                instructorMemberRepository.findInstructorApplications(InstructorMemberStatus.PENDING, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).status()).isEqualTo(InstructorMemberStatus.PENDING);
    }

    @Test
    @DisplayName("findInstructorApplications - APPROVED 필터 적용 시 PENDING 데이터는 포함되지 않음")
    void findInstructorApplications_approvedFilter_excludesPending() {
        Page<InstructorApplicationItemResult> result =
                instructorMemberRepository.findInstructorApplications(InstructorMemberStatus.APPROVED, PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("findInstructorApplications - REJECTED 필터 적용 시 반려된 신청만 반환")
    void findInstructorApplications_rejectedFilter_returnsRejected() {
        savedInstructorMember.reject("자격 요건 미달");

        Page<InstructorApplicationItemResult> result =
                instructorMemberRepository.findInstructorApplications(InstructorMemberStatus.REJECTED, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).status()).isEqualTo(InstructorMemberStatus.REJECTED);
    }

    @Test
    @DisplayName("findInstructorApplications - 페이징 적용 시 size 만큼만 반환")
    void findInstructorApplications_paging_limitsBySize() {
        // 두 번째 회원의 신청 추가
        Member member2 = memberRepository.save(
                Member.register("instructor2@test.com", "encodedPassword123!", "김철수", "010-9876-5432")
        );
        instructorMemberRepository.save(
                InstructorMember.register(member2.getId(), "FITNESS", "5년 경력 강사", "https://example.com/cert2.jpg")
        );

        Page<InstructorApplicationItemResult> result =
                instructorMemberRepository.findInstructorApplications(null, PageRequest.of(0, 1));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getTotalPages()).isEqualTo(2);
    }
    // endregion

    // region findInstructorApplicationDetail
    @Test
    @DisplayName("findInstructorApplicationDetail - memberId로 상세 조회 성공")
    void findInstructorApplicationDetail_success() {
        Optional<InstructorApplicationDetailResult> result =
                instructorMemberRepository.findInstructorApplicationDetail(savedMember.getId());

        assertThat(result).isPresent();
        InstructorApplicationDetailResult detail = result.get();
        assertThat(detail.memberId()).isEqualTo(savedMember.getId());
        assertThat(detail.memberName()).isEqualTo("홍길동");
        assertThat(detail.memberEmail()).isEqualTo("instructor@test.com");
        assertThat(detail.memberPhone()).isEqualTo("010-1234-5678");
        assertThat(detail.categoryCode()).isEqualTo("FITNESS");
        assertThat(detail.categoryName()).isEqualTo("피트니스");
        assertThat(detail.content()).isEqualTo("10년 경력의 피트니스 강사입니다");
        assertThat(detail.status()).isEqualTo(InstructorMemberStatus.PENDING);
        assertThat(detail.rejectReason()).isNull();
        assertThat(detail.respondedAt()).isNull();
    }

    @Test
    @DisplayName("findInstructorApplicationDetail - 반려된 신청 조회 시 rejectReason 포함")
    void findInstructorApplicationDetail_rejected_includesRejectReason() {
        savedInstructorMember.reject("자격 요건 미달");

        Optional<InstructorApplicationDetailResult> result =
                instructorMemberRepository.findInstructorApplicationDetail(savedMember.getId());

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(InstructorMemberStatus.REJECTED);
        assertThat(result.get().rejectReason()).isEqualTo("자격 요건 미달");
        assertThat(result.get().respondedAt()).isNotNull();
    }

    @Test
    @DisplayName("findInstructorApplicationDetail - 존재하지 않는 memberId면 빈 Optional 반환")
    void findInstructorApplicationDetail_notFound_returnsEmpty() {
        Optional<InstructorApplicationDetailResult> result =
                instructorMemberRepository.findInstructorApplicationDetail(UUID.randomUUID());

        assertThat(result).isEmpty();
    }
    // endregion

    // region findMyInstructorApplication
    @Test
    @DisplayName("findMyInstructorApplication - 본인 신청 조회 성공")
    void findMyInstructorApplication_success() {
        Optional<MyInstructorApplicationResult> result =
                instructorMemberRepository.findMyInstructorApplication(savedMember.getId());

        assertThat(result).isPresent();
        MyInstructorApplicationResult response = result.get();
        assertThat(response.categoryCode()).isEqualTo("FITNESS");
        assertThat(response.categoryName()).isEqualTo("피트니스");
        assertThat(response.content()).isEqualTo("10년 경력의 피트니스 강사입니다");
        assertThat(response.status()).isEqualTo(InstructorMemberStatus.PENDING);
        assertThat(response.rejectReason()).isNull();
    }

    @Test
    @DisplayName("findMyInstructorApplication - 반려 후 rejectReason 조회")
    void findMyInstructorApplication_rejected_includesRejectReason() {
        savedInstructorMember.reject("자격 요건 미달");

        Optional<MyInstructorApplicationResult> result =
                instructorMemberRepository.findMyInstructorApplication(savedMember.getId());

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(InstructorMemberStatus.REJECTED);
        assertThat(result.get().rejectReason()).isEqualTo("자격 요건 미달");
        assertThat(result.get().respondedAt()).isNotNull();
    }

    @Test
    @DisplayName("findMyInstructorApplication - 신청 이력 없으면 빈 Optional 반환")
    void findMyInstructorApplication_notFound_returnsEmpty() {
        Optional<MyInstructorApplicationResult> result =
                instructorMemberRepository.findMyInstructorApplication(UUID.randomUUID());

        assertThat(result).isEmpty();
    }
    // endregion
}
