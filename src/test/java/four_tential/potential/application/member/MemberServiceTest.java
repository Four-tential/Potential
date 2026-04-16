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
import four_tential.potential.domain.member.onboard_category.OnBoardCategoryRepository;
import four_tential.potential.presentation.member.model.request.OnBoardRequest;
import four_tential.potential.presentation.member.model.request.UpdateMyPageRequest;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
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
        given(courseCategoryRepository.existsByCode("FITNESS")).willReturn(true);
        given(courseCategoryRepository.existsByCode("ART")).willReturn(true);
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
    @DisplayName("온보딩 등록 - 존재하지 않는 카테고리 코드가 포함되면 ServiceErrorException 발생")
    void createOnBoarding_invalidCategory() {
        Member member = MemberFixture.defaultMember();
        given(memberOnBoardRepository.existsByMemberId(member.getId())).willReturn(false);
        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));
        given(courseCategoryRepository.existsByCode("FITNESS")).willReturn(true);
        given(courseCategoryRepository.existsByCode("INVALID")).willReturn(false);

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
}
