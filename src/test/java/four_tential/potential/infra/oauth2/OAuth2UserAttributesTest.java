package four_tential.potential.infra.oauth2;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.member.social.SocialProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuth2UserAttributesTest {

    @Test
    @DisplayName("Kakao - id/email/nickname 매핑")
    void fromKakao() {
        Map<String, Object> attributes = Map.of(
                "id", 12345L,
                "kakao_account", Map.of(
                        "email", "user@kakao.com",
                        "profile", Map.of("nickname", "카카오유저")
                )
        );

        OAuth2UserAttributes result = OAuth2UserAttributes.from("kakao", attributes);

        assertThat(result.provider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(result.providerId()).isEqualTo("12345");
        assertThat(result.email()).isEqualTo("user@kakao.com");
        assertThat(result.name()).isEqualTo("카카오유저");
    }

    @Test
    @DisplayName("Kakao - email 동의 누락 시 ERR_SOCIAL_PROFILE_INCOMPLETE")
    void fromKakao_missingEmail() {
        Map<String, Object> attributes = Map.of("id", 7777L);

        assertThatThrownBy(() -> OAuth2UserAttributes.from("kakao", attributes))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("소셜 계정에서 필수 프로필 정보를 받지 못했습니다, 동의 항목을 확인해주세요");
    }

    @Test
    @DisplayName("Google - email 누락 시 ERR_SOCIAL_PROFILE_INCOMPLETE")
    void fromGoogle_missingEmail() {
        Map<String, Object> attributes = Map.of("sub", "google-sub-1", "name", "이름만");

        assertThatThrownBy(() -> OAuth2UserAttributes.from("google", attributes))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("소셜 계정에서 필수 프로필 정보를 받지 못했습니다, 동의 항목을 확인해주세요");
    }

    @Test
    @DisplayName("Google - sub/email/name 매핑")
    void fromGoogle() {
        Map<String, Object> attributes = Map.of(
                "sub", "google-sub-1",
                "email", "user@gmail.com",
                "name", "구글유저"
        );

        OAuth2UserAttributes result = OAuth2UserAttributes.from("google", attributes);

        assertThat(result.provider()).isEqualTo(SocialProvider.GOOGLE);
        assertThat(result.providerId()).isEqualTo("google-sub-1");
        assertThat(result.email()).isEqualTo("user@gmail.com");
        assertThat(result.name()).isEqualTo("구글유저");
    }

    @Test
    @DisplayName("지원하지 않는 provider - ServiceErrorException")
    void unsupportedProvider() {
        assertThatThrownBy(() -> OAuth2UserAttributes.from("naver", Map.of()))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("지원하지 않는 소셜 로그인입니다");
    }
}
