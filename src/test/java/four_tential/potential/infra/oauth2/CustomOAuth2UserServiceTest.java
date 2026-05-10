package four_tential.potential.infra.oauth2;

import four_tential.potential.application.auth.SocialAuthService;
import four_tential.potential.application.auth.SocialEmailConflictException;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.MemberExceptionEnum;
import four_tential.potential.domain.member.fixture.MemberFixture;
import four_tential.potential.domain.member.member.Member;
import four_tential.potential.domain.member.social.SocialProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    @Mock
    private SocialAuthService socialAuthService;

    private TestableCustomOAuth2UserService newService(OAuth2User stub) {
        return new TestableCustomOAuth2UserService(socialAuthService, stub);
    }

    private OAuth2UserRequest kakaoRequest() {
        OAuth2UserRequest req = mock(OAuth2UserRequest.class);
        ClientRegistration registration = ClientRegistration.withRegistrationId("kakao")
                .clientId("c").clientSecret("s")
                .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://app/cb")
                .authorizationUri("https://auth").tokenUri("https://token")
                .userInfoUri("https://user").userNameAttributeName("id")
                .build();
        given(req.getClientRegistration()).willReturn(registration);
        return req;
    }

    private static OAuth2User kakaoOAuth2User() {
        Map<String, Object> attrs = Map.of(
                "id", 12345L,
                "kakao_account", Map.of(
                        "email", "user@kakao.com",
                        "profile", Map.of("nickname", "홍길동")
                )
        );
        return new DefaultOAuth2User(
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")),
                attrs, "id"
        );
    }

    @Test
    @DisplayName("loadUser - SocialAuthService.loginOrSignup 결과로 CustomOAuth2User 반환")
    void loadUser_success() {
        Member member = MemberFixture.defaultMember();
        given(socialAuthService.loginOrSignup(org.mockito.ArgumentMatchers.any(OAuth2UserAttributes.class)))
                .willReturn(member);

        TestableCustomOAuth2UserService service = newService(kakaoOAuth2User());

        OAuth2User loaded = service.loadUser(kakaoRequest());

        assertThat(loaded).isInstanceOf(CustomOAuth2User.class);
        CustomOAuth2User custom = (CustomOAuth2User) loaded;
        assertThat(custom.getEmail()).isEqualTo(member.getEmail());
        assertThat(custom.getRole()).isEqualTo(member.getRole());
    }

    @Test
    @DisplayName("loadUser - 이메일 충돌 시 OAuth2 description 에 challengeToken/email/provider 포함하여 InternalAuthenticationServiceException")
    void loadUser_emailConflict() {
        given(socialAuthService.loginOrSignup(org.mockito.ArgumentMatchers.any(OAuth2UserAttributes.class)))
                .willThrow(new SocialEmailConflictException("tok-1", "user@kakao.com", SocialProvider.KAKAO));

        TestableCustomOAuth2UserService service = newService(kakaoOAuth2User());

        assertThatThrownBy(() -> service.loadUser(kakaoRequest()))
                .isInstanceOf(InternalAuthenticationServiceException.class)
                .satisfies(ex -> {
                    Throwable cause = ex.getCause();
                    assertThat(cause).isInstanceOf(OAuth2AuthenticationException.class);
                    OAuth2AuthenticationException oauth2 = (OAuth2AuthenticationException) cause;
                    assertThat(oauth2.getError().getErrorCode()).isEqualTo("ERR_SOCIAL_EMAIL_CONFLICT");
                    assertThat(oauth2.getError().getDescription())
                            .contains("challengeToken=tok-1")
                            .contains("email=user@kakao.com")
                            .contains("provider=KAKAO");
                });
    }

    @Test
    @DisplayName("loadUser - ServiceErrorException 시 enum name 을 errorCode 로 묶어 InternalAuthenticationServiceException")
    void loadUser_serviceError() {
        given(socialAuthService.loginOrSignup(org.mockito.ArgumentMatchers.any(OAuth2UserAttributes.class)))
                .willThrow(new ServiceErrorException(MemberExceptionEnum.ERR_SUSPENDED));

        TestableCustomOAuth2UserService service = newService(kakaoOAuth2User());

        assertThatThrownBy(() -> service.loadUser(kakaoRequest()))
                .isInstanceOf(InternalAuthenticationServiceException.class)
                .satisfies(ex -> {
                    Throwable cause = ex.getCause();
                    assertThat(cause).isInstanceOf(OAuth2AuthenticationException.class);
                    OAuth2AuthenticationException oauth2 = (OAuth2AuthenticationException) cause;
                    assertThat(oauth2.getError().getErrorCode()).isEqualTo("ERR_SUSPENDED");
                });
    }

    private static class TestableCustomOAuth2UserService extends CustomOAuth2UserService {
        private final OAuth2User stub;

        TestableCustomOAuth2UserService(SocialAuthService socialAuthService, OAuth2User stub) {
            super(socialAuthService);
            this.stub = stub;
        }

        @Override
        protected OAuth2User delegateLoadUser(OAuth2UserRequest userRequest) {
            return stub;
        }
    }
}
