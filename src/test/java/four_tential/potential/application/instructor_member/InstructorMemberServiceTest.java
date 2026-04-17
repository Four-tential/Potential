package four_tential.potential.application.instructor_member;

import four_tential.potential.common.dto.PageResponse;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.course.course_category.CourseCategoryRepository;
import four_tential.potential.domain.member.fixture.InstructorMemberFixture;
import four_tential.potential.domain.member.fixture.MemberFixture;
import four_tential.potential.domain.member.instructor_member.InstructorMember;
import four_tential.potential.domain.member.instructor_member.InstructorMemberRepository;
import four_tential.potential.domain.member.instructor_member.InstructorMemberStatus;
import four_tential.potential.domain.member.member.Member;
import four_tential.potential.domain.member.member.MemberRepository;
import four_tential.potential.presentation.instructor_member.model.request.ApplyInstructorRequest;
import four_tential.potential.presentation.instructor_member.model.request.InstructorAction;
import four_tential.potential.presentation.instructor_member.model.request.InstructorActionRequest;
import four_tential.potential.presentation.instructor_member.model.response.ApplyInstructorResponse;
import four_tential.potential.presentation.instructor_member.model.response.InstructorActionResponse;
import four_tential.potential.presentation.instructor_member.model.response.InstructorApplicationDetail;
import four_tential.potential.presentation.instructor_member.model.response.InstructorApplicationItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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
class InstructorMemberServiceTest {

    @InjectMocks
    private InstructorMemberService instructorMemberService;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private InstructorMemberRepository instructorMemberRepository;

    @Mock
    private CourseCategoryRepository courseCategoryRepository;

    private static final ApplyInstructorRequest DEFAULT_REQUEST = new ApplyInstructorRequest(
            InstructorMemberFixture.DEFAULT_CATEGORY_CODE,
            InstructorMemberFixture.DEFAULT_CONTENT,
            InstructorMemberFixture.DEFAULT_IMAGE_URL
    );

    // region applyInstructor
    @Test
    @DisplayName("강사 신청 성공 - 최초 신청 시 새 InstructorMember 저장 후 PENDING 응답 반환")
    void applyInstructor_success_firstTime() {
        Member member = MemberFixture.defaultMember();

        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));
        given(courseCategoryRepository.existsByCode(InstructorMemberFixture.DEFAULT_CATEGORY_CODE)).willReturn(true);
        given(instructorMemberRepository.findByMemberId(member.getId())).willReturn(Optional.empty());

        ApplyInstructorResponse response = instructorMemberService.applyInstructor(member.getId(), DEFAULT_REQUEST);

        assertThat(response.status()).isEqualTo(InstructorMemberStatus.PENDING);
        assertThat(response.categoryCode()).isEqualTo(InstructorMemberFixture.DEFAULT_CATEGORY_CODE);
        verify(instructorMemberRepository).save(any(InstructorMember.class));
    }

    @Test
    @DisplayName("강사 신청 성공 - REJECTED 상태의 기존 신청이 있으면 재신청(update)으로 처리")
    void applyInstructor_success_reapply() {
        Member member = MemberFixture.defaultMember();
        InstructorMember rejected = InstructorMemberFixture.defaultInstructorMember();
        rejected.reject(InstructorMemberFixture.DEFAULT_REJECT_REASON);

        ApplyInstructorRequest reapplyRequest = new ApplyInstructorRequest(
                "YOGA", "요가 강사 자격증 보유", "https://example.com/yoga-cert.jpg"
        );

        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));
        given(courseCategoryRepository.existsByCode("YOGA")).willReturn(true);
        given(instructorMemberRepository.findByMemberId(member.getId())).willReturn(Optional.of(rejected));

        ApplyInstructorResponse response = instructorMemberService.applyInstructor(member.getId(), reapplyRequest);

        assertThat(response.status()).isEqualTo(InstructorMemberStatus.PENDING);
        assertThat(response.categoryCode()).isEqualTo("YOGA");
        // 재신청은 UPDATE이므로 save() 호출 없음
        verify(instructorMemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("강사 신청 - 이미 INSTRUCTOR 역할인 경우 ServiceErrorException 발생")
    void applyInstructor_alreadyInstructor() {
        Member member = MemberFixture.defaultMember();
        member.promoteToInstructor();

        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));

        assertThatThrownBy(() -> instructorMemberService.applyInstructor(member.getId(), DEFAULT_REQUEST))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("이미 강사로 등록된 회원입니다");
    }

    @Test
    @DisplayName("강사 신청 - 존재하지 않는 카테고리 코드이면 ServiceErrorException 발생")
    void applyInstructor_invalidCategory() {
        Member member = MemberFixture.defaultMember();
        ApplyInstructorRequest invalidRequest = new ApplyInstructorRequest(
                "INVALID", InstructorMemberFixture.DEFAULT_CONTENT, InstructorMemberFixture.DEFAULT_IMAGE_URL
        );

        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));
        given(courseCategoryRepository.existsByCode("INVALID")).willReturn(false);

        assertThatThrownBy(() -> instructorMemberService.applyInstructor(member.getId(), invalidRequest))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 카테고리입니다");
    }

    @Test
    @DisplayName("강사 신청 - PENDING 상태의 기존 신청이 있으면 ServiceErrorException 발생")
    void applyInstructor_alreadyPending() {
        Member member = MemberFixture.defaultMember();
        InstructorMember pending = InstructorMemberFixture.defaultInstructorMember();

        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));
        given(courseCategoryRepository.existsByCode(InstructorMemberFixture.DEFAULT_CATEGORY_CODE)).willReturn(true);
        given(instructorMemberRepository.findByMemberId(member.getId())).willReturn(Optional.of(pending));

        assertThatThrownBy(() -> instructorMemberService.applyInstructor(member.getId(), DEFAULT_REQUEST))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("이미 처리 중인 강사 신청이 있습니다");
    }

    @Test
    @DisplayName("강사 신청 - 존재하지 않는 회원이면 ServiceErrorException 발생")
    void applyInstructor_memberNotFound() {
        UUID unknownId = UUID.randomUUID();
        given(memberRepository.findById(unknownId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> instructorMemberService.applyInstructor(unknownId, DEFAULT_REQUEST))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 회원입니다");
    }
    // endregion

    // region processInstructorApplication
    @Test
    @DisplayName("강사 신청 승인 성공 - APPROVED 상태로 변경되고 회원 역할이 INSTRUCTOR로 전환")
    void processInstructorApplication_approve() {
        Member member = MemberFixture.defaultMember();
        InstructorMember instructorMember = InstructorMemberFixture.defaultInstructorMember();

        given(instructorMemberRepository.findByMemberId(member.getId())).willReturn(Optional.of(instructorMember));
        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));

        InstructorActionRequest request = new InstructorActionRequest(InstructorAction.APPROVE, null);
        InstructorActionResponse response = instructorMemberService.processInstructorApplication(member.getId(), request);

        assertThat(response.status()).isEqualTo(InstructorMemberStatus.APPROVED);
        assertThat(response.respondedAt()).isNotNull();
        assertThat(member.getRole().name()).isEqualTo("ROLE_INSTRUCTOR");
    }

    @Test
    @DisplayName("강사 신청 반려 성공 - REJECTED 상태로 변경되고 반려 사유 저장")
    void processInstructorApplication_reject() {
        Member member = MemberFixture.defaultMember();
        InstructorMember instructorMember = InstructorMemberFixture.defaultInstructorMember();

        given(instructorMemberRepository.findByMemberId(member.getId())).willReturn(Optional.of(instructorMember));

        InstructorActionRequest request = new InstructorActionRequest(InstructorAction.REJECT, InstructorMemberFixture.DEFAULT_REJECT_REASON);
        InstructorActionResponse response = instructorMemberService.processInstructorApplication(member.getId(), request);

        assertThat(response.status()).isEqualTo(InstructorMemberStatus.REJECTED);
        assertThat(response.respondedAt()).isNotNull();
    }

    @Test
    @DisplayName("강사 신청 처리 - 신청 내역 없으면 ServiceErrorException 발생")
    void processInstructorApplication_notFound() {
        UUID unknownId = UUID.randomUUID();
        given(instructorMemberRepository.findByMemberId(unknownId)).willReturn(Optional.empty());

        InstructorActionRequest request = new InstructorActionRequest(InstructorAction.APPROVE, null);

        assertThatThrownBy(() -> instructorMemberService.processInstructorApplication(unknownId, request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("강사 신청 내역이 존재하지 않습니다");
    }

    @Test
    @DisplayName("강사 신청 처리 - 이미 처리된 신청이면 ServiceErrorException 발생 (409)")
    void processInstructorApplication_alreadyProcessed() {
        Member member = MemberFixture.defaultMember();
        InstructorMember instructorMember = InstructorMemberFixture.defaultInstructorMember();
        instructorMember.approve(); // APPROVED 상태로 변경

        given(instructorMemberRepository.findByMemberId(member.getId())).willReturn(Optional.of(instructorMember));
        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));

        InstructorActionRequest request = new InstructorActionRequest(InstructorAction.APPROVE, null);

        assertThatThrownBy(() -> instructorMemberService.processInstructorApplication(member.getId(), request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("이미 처리된 강사 신청입니다");
    }

    @Test
    @DisplayName("강사 신청 반려 - 반려 사유 없으면 ServiceErrorException 발생 (400)")
    void processInstructorApplication_rejectWithoutReason() {
        Member member = MemberFixture.defaultMember();
        InstructorMember instructorMember = InstructorMemberFixture.defaultInstructorMember();

        given(instructorMemberRepository.findByMemberId(member.getId())).willReturn(Optional.of(instructorMember));

        InstructorActionRequest request = new InstructorActionRequest(InstructorAction.REJECT, null);

        assertThatThrownBy(() -> instructorMemberService.processInstructorApplication(member.getId(), request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("거절 사유를 입력해주세요");
    }
    // endregion

    // region getInstructorApplicationDetail
    @Test
    @DisplayName("강사 신청 상세 조회 성공 - memberId로 상세 정보 반환")
    void getInstructorApplicationDetail_success() {
        Member member = MemberFixture.defaultMember();
        InstructorApplicationDetail detail = new InstructorApplicationDetail(
                member.getId(), "홍길동", "hong@test.com", "010-1234-5678",
                "FITNESS", "피트니스",
                InstructorMemberFixture.DEFAULT_CONTENT,
                InstructorMemberFixture.DEFAULT_IMAGE_URL,
                InstructorMemberStatus.PENDING, null, null, null
        );

        given(instructorMemberRepository.findInstructorApplicationDetail(member.getId()))
                .willReturn(Optional.of(detail));

        InstructorApplicationDetail response =
                instructorMemberService.getInstructorApplicationDetail(member.getId());

        assertThat(response.memberId()).isEqualTo(member.getId());
        assertThat(response.status()).isEqualTo(InstructorMemberStatus.PENDING);
        assertThat(response.categoryCode()).isEqualTo("FITNESS");
        assertThat(response.rejectReason()).isNull();
    }

    @Test
    @DisplayName("강사 신청 상세 조회 - 신청 내역 없으면 ServiceErrorException 발생")
    void getInstructorApplicationDetail_notFound() {
        UUID unknownId = UUID.randomUUID();
        given(instructorMemberRepository.findInstructorApplicationDetail(unknownId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> instructorMemberService.getInstructorApplicationDetail(unknownId))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("강사 신청 내역이 존재하지 않습니다");
    }
    // endregion

    // region getInstructorApplications
    @Test
    @DisplayName("강사 신청 목록 조회 성공 - status 필터 없이 전체 목록 반환")
    void getInstructorApplications_noFilter() {
        Pageable pageable = PageRequest.of(0, 10);
        List<InstructorApplicationItem> items = List.of(
                new InstructorApplicationItem(
                        UUID.randomUUID(), "홍길동", "hong@test.com",
                        "FITNESS", "피트니스", InstructorMemberStatus.PENDING, null
                )
        );
        given(instructorMemberRepository.findInstructorApplications(null, pageable))
                .willReturn(new PageImpl<>(items, pageable, 1));

        PageResponse<InstructorApplicationItem> response =
                instructorMemberService.getInstructorApplications(null, pageable);

        assertThat(response.content()).hasSize(1);
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content().get(0).status()).isEqualTo(InstructorMemberStatus.PENDING);
    }

    @Test
    @DisplayName("강사 신청 목록 조회 성공 - PENDING 상태 필터 적용")
    void getInstructorApplications_withStatusFilter() {
        Pageable pageable = PageRequest.of(0, 10);
        List<InstructorApplicationItem> items = List.of(
                new InstructorApplicationItem(
                        UUID.randomUUID(), "김철수", "kim@test.com",
                        "YOGA", "요가", InstructorMemberStatus.PENDING, null
                )
        );
        given(instructorMemberRepository.findInstructorApplications(InstructorMemberStatus.PENDING, pageable))
                .willReturn(new PageImpl<>(items, pageable, 1));

        PageResponse<InstructorApplicationItem> response =
                instructorMemberService.getInstructorApplications(InstructorMemberStatus.PENDING, pageable);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).status()).isEqualTo(InstructorMemberStatus.PENDING);
    }

    @Test
    @DisplayName("강사 신청 목록 조회 성공 - 결과 없으면 빈 페이지 반환")
    void getInstructorApplications_empty() {
        Pageable pageable = PageRequest.of(0, 10);
        given(instructorMemberRepository.findInstructorApplications(InstructorMemberStatus.APPROVED, pageable))
                .willReturn(new PageImpl<>(List.of(), pageable, 0));

        PageResponse<InstructorApplicationItem> response =
                instructorMemberService.getInstructorApplications(InstructorMemberStatus.APPROVED, pageable);

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
        assertThat(response.isLast()).isTrue();
    }
    // endregion
}
