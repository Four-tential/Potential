package four_tential.potential.application.member;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.course.course_category.CourseCategoryRepository;
import four_tential.potential.domain.member.fixture.MemberFixture;
import four_tential.potential.domain.member.fixture.MemberOnBoardFixture;
import four_tential.potential.domain.member.member.Member;
import four_tential.potential.domain.member.member.MemberRepository;
import four_tential.potential.domain.member.member_onboard.MemberOnBoard;
import four_tential.potential.domain.member.member_onboard.MemberOnBoardGoal;
import four_tential.potential.domain.member.member_onboard.MemberOnBoardRepository;
import four_tential.potential.domain.member.onboard_category.MemberOnBoardCategory;
import four_tential.potential.domain.member.onboard_category.OnBoardCategoryRepository;
import four_tential.potential.presentation.member.model.request.ChangePasswordRequest;
import four_tential.potential.presentation.member.model.request.OnBoardRequest;
import four_tential.potential.presentation.member.model.request.UpdateMyPageRequest;
import four_tential.potential.presentation.member.model.request.UpdateOnBoardRequest;
import four_tential.potential.presentation.member.model.response.MyPageResponse;
import four_tential.potential.presentation.member.model.response.OnBoardResponse;
import four_tential.potential.presentation.member.model.response.UpdateMyPageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MemberOnBoardRepository memberOnBoardRepository;

    @Mock
    private OnBoardCategoryRepository onBoardCategoryRepository;

    @Mock
    private CourseCategoryRepository courseCategoryRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private MemberService memberService;

    private static final String DEFAULT_IMAGE_URL = "https://bucketurl/default-profile-image.png";
    private static final String CUSTOM_IMAGE_URL = MemberFixture.DEFAULT_PROFILE_IMAGE_URL;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(memberService, "defaultProfileImageUrl", DEFAULT_IMAGE_URL);
    }

    // region getMyPageInfo
    @Test
    @DisplayName("마이페이지 조회 성공 - 프로필 이미지가 있으면 실제 URL 반환")
    void getMyPageInfo_withProfileImage() {
        Member member = MemberFixture.defaultMember();
        member.setProfileImageUrl(CUSTOM_IMAGE_URL);
        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));

        MyPageResponse response = memberService.getMyPageInfo(member.getId());

        assertThat(response.email()).isEqualTo(MemberFixture.DEFAULT_EMAIL);
        assertThat(response.profileImageUrl()).isEqualTo(CUSTOM_IMAGE_URL);
    }

    @Test
    @DisplayName("마이페이지 조회 성공 - 프로필 이미지가 없으면 기본 URL 반환")
    void getMyPageInfo_withoutProfileImage() {
        Member member = MemberFixture.defaultMember(); // profileImageUrl = null
        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));

        MyPageResponse response = memberService.getMyPageInfo(member.getId());

        assertThat(response.profileImageUrl()).isEqualTo(DEFAULT_IMAGE_URL);
    }

    @Test
    @DisplayName("마이페이지 조회 - 존재하지 않는 회원이면 ServiceErrorException 발생")
    void getMyPageInfo_memberNotFound() {
        UUID unknownId = UUID.randomUUID();
        given(memberRepository.findById(unknownId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.getMyPageInfo(unknownId))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 회원입니다");
    }
    // endregion

    // region updateMyPageInfo
    @Test
    @DisplayName("마이페이지 수정 성공 - phone만 전송 시 phone 변경, 이미지 유지")
    void updateMyPageInfo_phoneOnly() {
        Member member = MemberFixture.defaultMember();
        member.setProfileImageUrl(CUSTOM_IMAGE_URL);
        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));

        UpdateMyPageRequest request = new UpdateMyPageRequest("010-9999-9999", null);
        UpdateMyPageResponse response = memberService.updateMyPageInfo(member.getId(), request);

        assertThat(response.phone()).isEqualTo("010-9999-9999");
        assertThat(response.profileImageUrl()).isEqualTo(CUSTOM_IMAGE_URL); // 기존 이미지 유지
    }

    @Test
    @DisplayName("마이페이지 수정 성공 - profileImageUrl만 전송 시 이미지 변경, phone 유지")
    void updateMyPageInfo_profileImageOnly() {
        Member member = MemberFixture.defaultMember();
        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));

        UpdateMyPageRequest request = new UpdateMyPageRequest(null, CUSTOM_IMAGE_URL);
        UpdateMyPageResponse response = memberService.updateMyPageInfo(member.getId(), request);

        assertThat(response.phone()).isEqualTo(MemberFixture.DEFAULT_PHONE); // 기존 phone 유지
        assertThat(response.profileImageUrl()).isEqualTo(CUSTOM_IMAGE_URL);
    }

    @Test
    @DisplayName("마이페이지 수정 성공 - phone, profileImageUrl 모두 전송 시 둘 다 변경")
    void updateMyPageInfo_both() {
        Member member = MemberFixture.defaultMember();
        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));

        UpdateMyPageRequest request = new UpdateMyPageRequest("010-9999-9999", CUSTOM_IMAGE_URL);
        UpdateMyPageResponse response = memberService.updateMyPageInfo(member.getId(), request);

        assertThat(response.phone()).isEqualTo("010-9999-9999");
        assertThat(response.profileImageUrl()).isEqualTo(CUSTOM_IMAGE_URL);
    }

    @Test
    @DisplayName("마이페이지 수정 - 모든 필드가 null이면 ServiceErrorException 발생")
    void updateMyPageInfo_allNull() {
        UpdateMyPageRequest request = new UpdateMyPageRequest(null, null);

        assertThatThrownBy(() -> memberService.updateMyPageInfo(UUID.randomUUID(), request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("수정할 항목을 하나 이상 입력해주세요");
    }

    @Test
    @DisplayName("마이페이지 수정 - 존재하지 않는 회원이면 ServiceErrorException 발생")
    void updateMyPageInfo_memberNotFound() {
        UUID unknownId = UUID.randomUUID();
        given(memberRepository.findById(unknownId)).willReturn(Optional.empty());

        UpdateMyPageRequest request = new UpdateMyPageRequest("010-9999-9999", null);

        assertThatThrownBy(() -> memberService.updateMyPageInfo(unknownId, request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 회원입니다");
    }
    // endregion

    // region createOnBoarding
    @Test
    @DisplayName("온보딩 등록 성공 - 목표와 카테고리가 저장되고 멤버 온보딩 완료 처리")
    void createOnBoarding_success() {
        Member member = MemberFixture.defaultMember();
        MemberOnBoard onBoard = MemberOnBoardFixture.defaultMemberOnBoard();
        List<String> categoryCodes = List.of("FITNESS", "ART");

        given(memberOnBoardRepository.existsByMemberId(member.getId())).willReturn(false);
        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));
        given(courseCategoryRepository.findExistingCodes(any())).willReturn(Set.of("FITNESS", "ART"));
        given(memberOnBoardRepository.save(any())).willReturn(onBoard);

        OnBoardRequest request = new OnBoardRequest(MemberOnBoardGoal.HOBBY, categoryCodes);
        OnBoardResponse response = memberService.createOnBoarding(member.getId(), request);

        assertThat(response.goal()).isEqualTo("HOBBY");
        assertThat(response.categoryCodes()).containsExactly("FITNESS", "ART");
        assertThat(member.isHasOnboarding()).isTrue();
        verify(onBoardCategoryRepository).saveAll(any());
    }

    @Test
    @DisplayName("온보딩 등록 - 이미 온보딩을 완료한 회원이면 ServiceErrorException 발생")
    void createOnBoarding_alreadyOnboarded() {
        Member member = MemberFixture.memberWithOnboarding();
        given(memberOnBoardRepository.existsByMemberId(member.getId())).willReturn(true);

        OnBoardRequest request = new OnBoardRequest(MemberOnBoardGoal.HOBBY, List.of("FITNESS"));

        assertThatThrownBy(() -> memberService.createOnBoarding(member.getId(), request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("이미 온보딩을 완료한 회원입니다");
    }

    @Test
    @DisplayName("온보딩 등록 - 중복 카테고리 코드가 포함되면 ServiceErrorException 발생")
    void createOnBoarding_duplicatedCategory() {
        Member member = MemberFixture.defaultMember();
        given(memberOnBoardRepository.existsByMemberId(member.getId())).willReturn(false);
        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));

        OnBoardRequest request = new OnBoardRequest(MemberOnBoardGoal.HOBBY, List.of("FITNESS", "FITNESS"));

        assertThatThrownBy(() -> memberService.createOnBoarding(member.getId(), request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("중복된 카테고리 코드가 포함되어 있습니다");
    }

    @Test
    @DisplayName("온보딩 등록 - 존재하지 않는 카테고리 코드가 포함되면 ServiceErrorException 발생")
    void createOnBoarding_invalidCategory() {
        Member member = MemberFixture.defaultMember();
        given(memberOnBoardRepository.existsByMemberId(member.getId())).willReturn(false);
        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));
        given(courseCategoryRepository.findExistingCodes(any())).willReturn(Set.of("FITNESS")); // INVALID 미포함

        OnBoardRequest request = new OnBoardRequest(MemberOnBoardGoal.HOBBY, List.of("FITNESS", "INVALID"));

        assertThatThrownBy(() -> memberService.createOnBoarding(member.getId(), request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 카테고리입니다");
    }

    @Test
    @DisplayName("온보딩 등록 - 존재하지 않는 회원이면 ServiceErrorException 발생")
    void createOnBoarding_memberNotFound() {
        UUID unknownId = UUID.randomUUID();
        given(memberOnBoardRepository.existsByMemberId(unknownId)).willReturn(false);
        given(memberRepository.findById(unknownId)).willReturn(Optional.empty());

        OnBoardRequest request = new OnBoardRequest(MemberOnBoardGoal.HOBBY, List.of("FITNESS"));

        assertThatThrownBy(() -> memberService.createOnBoarding(unknownId, request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 회원입니다");
    }
    // endregion

    // region updateOnBoarding
    @Test
    @DisplayName("온보딩 수정 성공 - 기존과 다른 카테고리로 변경 시 삭제 대상은 지우고 추가 대상만 저장")
    void updateOnBoarding_diffUpdate() {
        Member member = MemberFixture.memberWithOnboarding();
        MemberOnBoard onBoard = MemberOnBoardFixture.defaultMemberOnBoard();

        // 기존: FITNESS / 요청: COOK → FITNESS 삭제, COOK 추가
        MemberOnBoardCategory existingCategory = MemberOnBoardCategory.register(member, "FITNESS");
        given(memberOnBoardRepository.findByMemberId(member.getId())).willReturn(Optional.of(onBoard));
        given(onBoardCategoryRepository.findByMemberId(member.getId())).willReturn(List.of(existingCategory));
        given(courseCategoryRepository.findExistingCodes(any())).willReturn(Set.of("COOK"));
        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));

        UpdateOnBoardRequest request = new UpdateOnBoardRequest(MemberOnBoardGoal.STRESS_OUT, List.of("COOK"));
        OnBoardResponse response = memberService.updateOnBoarding(member.getId(), request);

        assertThat(response.goal()).isEqualTo("STRESS_OUT");
        assertThat(response.categoryCodes()).containsExactly("COOK");
        verify(onBoardCategoryRepository).deleteByMemberIdAndCategoryCodeIn(eq(member.getId()), any());
        verify(onBoardCategoryRepository).saveAll(any());
    }

    @Test
    @DisplayName("온보딩 수정 성공 - 기존 카테고리 포함하여 카테고리 추가 시 삭제 없이 신규 코드만 저장")
    void updateOnBoarding_addCategory() {
        Member member = MemberFixture.memberWithOnboarding();
        MemberOnBoard onBoard = MemberOnBoardFixture.defaultMemberOnBoard();

        // 기존: FITNESS / 요청: FITNESS, COOK → FITNESS 유지, COOK만 추가
        MemberOnBoardCategory existingCategory = MemberOnBoardCategory.register(member, "FITNESS");
        given(memberOnBoardRepository.findByMemberId(member.getId())).willReturn(Optional.of(onBoard));
        given(onBoardCategoryRepository.findByMemberId(member.getId())).willReturn(List.of(existingCategory));
        given(courseCategoryRepository.findExistingCodes(any())).willReturn(Set.of("FITNESS", "COOK"));
        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));

        UpdateOnBoardRequest request = new UpdateOnBoardRequest(null, List.of("FITNESS", "COOK"));
        OnBoardResponse response = memberService.updateOnBoarding(member.getId(), request);

        assertThat(response.categoryCodes()).containsExactlyInAnyOrder("FITNESS", "COOK");
        verify(onBoardCategoryRepository, never()).deleteByMemberIdAndCategoryCodeIn(any(), any());
        verify(onBoardCategoryRepository).saveAll(any());
    }

    @Test
    @DisplayName("온보딩 수정 성공 - 목표만 전송 시 목표만 변경, 카테고리 조회 결과 그대로 반환")
    void updateOnBoarding_goalOnly() {
        Member member = MemberFixture.memberWithOnboarding();
        MemberOnBoard onBoard = MemberOnBoardFixture.defaultMemberOnBoard();

        MemberOnBoardCategory existingCategory = MemberOnBoardCategory.register(member, "FITNESS");
        given(memberOnBoardRepository.findByMemberId(member.getId())).willReturn(Optional.of(onBoard));
        given(onBoardCategoryRepository.findByMemberId(member.getId())).willReturn(List.of(existingCategory));

        UpdateOnBoardRequest request = new UpdateOnBoardRequest(MemberOnBoardGoal.STRESS_OUT, null);
        OnBoardResponse response = memberService.updateOnBoarding(member.getId(), request);

        assertThat(response.goal()).isEqualTo("STRESS_OUT");
        assertThat(response.categoryCodes()).containsExactly("FITNESS");
        verify(onBoardCategoryRepository, never()).deleteByMemberIdAndCategoryCodeIn(any(), any());
        verify(onBoardCategoryRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("온보딩 수정 - 모든 필드가 null이면 ServiceErrorException 발생")
    void updateOnBoarding_allNull() {
        UpdateOnBoardRequest request = new UpdateOnBoardRequest(null, null);

        assertThatThrownBy(() -> memberService.updateOnBoarding(UUID.randomUUID(), request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("수정할 항목을 하나 이상 입력해주세요");
    }

    @Test
    @DisplayName("온보딩 수정 - 온보딩 미설정 회원이면 ServiceErrorException 발생")
    void updateOnBoarding_notFound() {
        UUID unknownId = UUID.randomUUID();
        given(memberOnBoardRepository.findByMemberId(unknownId)).willReturn(Optional.empty());

        UpdateOnBoardRequest request = new UpdateOnBoardRequest(MemberOnBoardGoal.STRESS_OUT, List.of("COOK"));

        assertThatThrownBy(() -> memberService.updateOnBoarding(unknownId, request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("온보딩 정보가 존재하지 않습니다");
    }

    @Test
    @DisplayName("온보딩 수정 - 중복 카테고리 코드가 포함되면 ServiceErrorException 발생")
    void updateOnBoarding_duplicatedCategory() {
        Member member = MemberFixture.memberWithOnboarding();
        MemberOnBoard onBoard = MemberOnBoardFixture.defaultMemberOnBoard();

        given(memberOnBoardRepository.findByMemberId(member.getId())).willReturn(Optional.of(onBoard));
        given(onBoardCategoryRepository.findByMemberId(member.getId())).willReturn(List.of());

        UpdateOnBoardRequest request = new UpdateOnBoardRequest(null, List.of("FITNESS", "FITNESS"));

        assertThatThrownBy(() -> memberService.updateOnBoarding(member.getId(), request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("중복된 카테고리 코드가 포함되어 있습니다");
    }

    @Test
    @DisplayName("온보딩 수정 - 존재하지 않는 카테고리 코드이면 ServiceErrorException 발생")
    void updateOnBoarding_invalidCategory() {
        Member member = MemberFixture.memberWithOnboarding();
        MemberOnBoard onBoard = MemberOnBoardFixture.defaultMemberOnBoard();

        given(memberOnBoardRepository.findByMemberId(member.getId())).willReturn(Optional.of(onBoard));
        given(onBoardCategoryRepository.findByMemberId(member.getId())).willReturn(List.of());
        given(courseCategoryRepository.findExistingCodes(any())).willReturn(Set.of()); // INVALID 미포함

        UpdateOnBoardRequest request = new UpdateOnBoardRequest(MemberOnBoardGoal.STRESS_OUT, List.of("INVALID"));

        assertThatThrownBy(() -> memberService.updateOnBoarding(member.getId(), request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 카테고리입니다");
    }
    // endregion

    // region changePassword
    @Test
    @DisplayName("비밀번호 변경 성공 - 현재 비밀번호 일치 시 새 비밀번호로 변경")
    void changePassword_success() {
        Member member = MemberFixture.defaultMember();
        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));
        given(passwordEncoder.matches("OldP@ss1!", MemberFixture.DEFAULT_PASSWORD)).willReturn(true);
        given(passwordEncoder.matches("NewP@ss2!", MemberFixture.DEFAULT_PASSWORD)).willReturn(false);
        given(passwordEncoder.encode("NewP@ss2!")).willReturn("encodedNewPassword");

        memberService.changePassword(member.getId(), new ChangePasswordRequest("OldP@ss1!", "NewP@ss2!"));

        assertThat(member.getPassword()).isEqualTo("encodedNewPassword");
    }

    @Test
    @DisplayName("비밀번호 변경 실패 - 현재 비밀번호 불일치 시 BAD_REQUEST")
    void changePassword_wrongCurrentPassword_throwsBadRequest() {
        Member member = MemberFixture.defaultMember();
        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));
        given(passwordEncoder.matches("WrongP@ss!", MemberFixture.DEFAULT_PASSWORD)).willReturn(false);

        assertThatThrownBy(() ->
                memberService.changePassword(member.getId(), new ChangePasswordRequest("WrongP@ss!", "NewP@ss2!"))
        )
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("현재 비밀번호가 올바르지 않습니다");
    }

    @Test
    @DisplayName("비밀번호 변경 실패 - 현재와 동일한 비밀번호로 변경 시 BAD_REQUEST")
    void changePassword_sameAsCurrentPassword_throwsBadRequest() {
        Member member = MemberFixture.defaultMember();
        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));
        given(passwordEncoder.matches("P@ssw0rd1!", MemberFixture.DEFAULT_PASSWORD)).willReturn(true);

        assertThatThrownBy(() ->
                memberService.changePassword(member.getId(), new ChangePasswordRequest("P@ssw0rd1!", "P@ssw0rd1!"))
        )
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("현재 비밀번호와 동일한 비밀번호로 변경할 수 없습니다");
    }
    // endregion
}
