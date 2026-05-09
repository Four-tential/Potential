package four_tential.potential.application.auth;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.member.fixture.MemberFixture;
import four_tential.potential.domain.member.member.Member;
import four_tential.potential.domain.member.member.MemberRepository;
import four_tential.potential.domain.member.social.MemberSocialAccount;
import four_tential.potential.domain.member.social.MemberSocialAccountRepository;
import four_tential.potential.domain.member.social.SocialProvider;
import four_tential.potential.infra.oauth2.OAuth2UserAttributes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SocialAuthServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MemberSocialAccountRepository socialAccountRepository;

    @InjectMocks
    private SocialAuthService socialAuthService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(socialAuthService, "defaultProfileImageUrl", "https://cdn.example.com/default.jpg");
    }

    @Test
    @DisplayName("기존 소셜 계정 존재 - 매칭된 회원 반환")
    void loginOrSignup_existingAccount() {
        OAuth2UserAttributes attributes = new OAuth2UserAttributes(SocialProvider.KAKAO, "kakao-1", "user@kakao.com", "홍길동");
        UUID memberId = UUID.randomUUID();
        MemberSocialAccount account = MemberSocialAccount.link(memberId, SocialProvider.KAKAO, "kakao-1", "user@kakao.com");
        Member member = MemberFixture.defaultMember();

        given(socialAccountRepository.findByProviderAndProviderId(SocialProvider.KAKAO, "kakao-1"))
                .willReturn(Optional.of(account));
        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));

        Member result = socialAuthService.loginOrSignup(attributes);

        assertThat(result).isSameAs(member);
        verify(memberRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("신규 소셜 가입 - 회원/소셜 계정 모두 저장")
    void loginOrSignup_newSocialMember() {
        OAuth2UserAttributes attributes = new OAuth2UserAttributes(SocialProvider.GOOGLE, "google-1", "new@google.com", "신규유저");

        given(socialAccountRepository.findByProviderAndProviderId(SocialProvider.GOOGLE, "google-1"))
                .willReturn(Optional.empty());
        given(memberRepository.existsByEmail("new@google.com")).willReturn(false);

        Member result = socialAuthService.loginOrSignup(attributes);

        assertThat(result.getEmail()).isEqualTo("new@google.com");
        assertThat(result.getName()).isEqualTo("신규유저");
        assertThat(result.getPassword()).isNull();
        assertThat(result.requiresPhoneSetup()).isTrue();
        verify(memberRepository).save(result);

        ArgumentCaptor<MemberSocialAccount> captor = ArgumentCaptor.forClass(MemberSocialAccount.class);
        verify(socialAccountRepository).save(captor.capture());
        assertThat(captor.getValue().getProvider()).isEqualTo(SocialProvider.GOOGLE);
        assertThat(captor.getValue().getProviderId()).isEqualTo("google-1");
    }

    @Test
    @DisplayName("신규 소셜 가입 - 동일 이메일 로컬 회원 존재 시 ERR_SOCIAL_EMAIL_CONFLICT")
    void loginOrSignup_emailConflict() {
        OAuth2UserAttributes attributes = new OAuth2UserAttributes(SocialProvider.GOOGLE, "google-1", "dup@google.com", "유저");

        given(socialAccountRepository.findByProviderAndProviderId(SocialProvider.GOOGLE, "google-1"))
                .willReturn(Optional.empty());
        given(memberRepository.existsByEmail("dup@google.com")).willReturn(true);

        assertThatThrownBy(() -> socialAuthService.loginOrSignup(attributes))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("동일 이메일로 가입된 계정이 있습니다, 로그인 후 마이페이지에서 연동해주세요");

        verify(memberRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(socialAccountRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("수동 연동 - 정상 케이스")
    void linkExistingMember_success() {
        UUID memberId = UUID.randomUUID();
        OAuth2UserAttributes attributes = new OAuth2UserAttributes(SocialProvider.KAKAO, "kakao-1", "user@kakao.com", "홍길동");

        given(socialAccountRepository.findByProviderAndProviderId(SocialProvider.KAKAO, "kakao-1"))
                .willReturn(Optional.empty());
        given(socialAccountRepository.existsByMemberIdAndProvider(memberId, SocialProvider.KAKAO)).willReturn(false);

        socialAuthService.linkExistingMember(memberId, attributes);

        ArgumentCaptor<MemberSocialAccount> captor = ArgumentCaptor.forClass(MemberSocialAccount.class);
        verify(socialAccountRepository).save(captor.capture());
        assertThat(captor.getValue().getMemberId()).isEqualTo(memberId);
        assertThat(captor.getValue().getProvider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(captor.getValue().getProviderId()).isEqualTo("kakao-1");
    }

    @Test
    @DisplayName("수동 연동 - 다른 회원이 이미 사용 중인 소셜 계정이면 ERR_SOCIAL_LINK_DUPLICATED")
    void linkExistingMember_alreadyLinkedToOther() {
        UUID memberId = UUID.randomUUID();
        UUID otherMemberId = UUID.randomUUID();
        OAuth2UserAttributes attributes = new OAuth2UserAttributes(SocialProvider.KAKAO, "kakao-1", "user@kakao.com", "홍길동");
        MemberSocialAccount existing = MemberSocialAccount.link(otherMemberId, SocialProvider.KAKAO, "kakao-1", "user@kakao.com");

        given(socialAccountRepository.findByProviderAndProviderId(SocialProvider.KAKAO, "kakao-1"))
                .willReturn(Optional.of(existing));

        assertThatThrownBy(() -> socialAuthService.linkExistingMember(memberId, attributes))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("이미 다른 계정에 연동된 소셜 계정입니다");
        verify(socialAccountRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("수동 연동 - 동일 회원이 이미 연동한 소셜 계정이면 ERR_SOCIAL_ALREADY_LINKED")
    void linkExistingMember_alreadyLinkedToSelf() {
        UUID memberId = UUID.randomUUID();
        OAuth2UserAttributes attributes = new OAuth2UserAttributes(SocialProvider.KAKAO, "kakao-1", "user@kakao.com", "홍길동");
        MemberSocialAccount existing = MemberSocialAccount.link(memberId, SocialProvider.KAKAO, "kakao-1", "user@kakao.com");

        given(socialAccountRepository.findByProviderAndProviderId(SocialProvider.KAKAO, "kakao-1"))
                .willReturn(Optional.of(existing));

        assertThatThrownBy(() -> socialAuthService.linkExistingMember(memberId, attributes))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("이미 동일 provider 의 소셜 계정이 연동되어 있습니다");
    }

    @Test
    @DisplayName("수동 연동 - 동일 provider 로 이미 다른 소셜 계정 연동 시 ERR_SOCIAL_ALREADY_LINKED")
    void linkExistingMember_providerAlreadyUsed() {
        UUID memberId = UUID.randomUUID();
        OAuth2UserAttributes attributes = new OAuth2UserAttributes(SocialProvider.KAKAO, "kakao-1", "user@kakao.com", "홍길동");

        given(socialAccountRepository.findByProviderAndProviderId(SocialProvider.KAKAO, "kakao-1"))
                .willReturn(Optional.empty());
        given(socialAccountRepository.existsByMemberIdAndProvider(memberId, SocialProvider.KAKAO)).willReturn(true);

        assertThatThrownBy(() -> socialAuthService.linkExistingMember(memberId, attributes))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("이미 동일 provider 의 소셜 계정이 연동되어 있습니다");
        verify(socialAccountRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("연동 해제 - 정상 케이스")
    void unlinkSocialAccount_success() {
        UUID memberId = UUID.randomUUID();
        given(socialAccountRepository.existsByMemberIdAndProvider(memberId, SocialProvider.KAKAO)).willReturn(true);

        socialAuthService.unlinkSocialAccount(memberId, SocialProvider.KAKAO);

        verify(socialAccountRepository).deleteByMemberIdAndProvider(memberId, SocialProvider.KAKAO);
    }

    @Test
    @DisplayName("연동 해제 - 연동 없으면 ERR_SOCIAL_LINK_NOT_FOUND")
    void unlinkSocialAccount_notFound() {
        UUID memberId = UUID.randomUUID();
        given(socialAccountRepository.existsByMemberIdAndProvider(memberId, SocialProvider.KAKAO)).willReturn(false);

        assertThatThrownBy(() -> socialAuthService.unlinkSocialAccount(memberId, SocialProvider.KAKAO))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("해제할 소셜 연동 정보가 없습니다");
        verify(socialAccountRepository, never()).deleteByMemberIdAndProvider(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
