package four_tential.potential.infra.oauth2;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.MemberExceptionEnum;
import four_tential.potential.domain.member.social.SocialProvider;

import java.util.Map;

public record OAuth2UserAttributes(
        SocialProvider provider,
        String providerId,
        String email,
        String name
) {

    public static OAuth2UserAttributes from(String registrationId, Map<String, Object> attributes) {
        return switch (registrationId.toLowerCase()) {
            case "kakao" -> fromKakao(attributes);
            case "google" -> fromGoogle(attributes);
            default -> throw new ServiceErrorException(MemberExceptionEnum.ERR_SOCIAL_PROVIDER_NOT_SUPPORTED);
        };
    }

    @SuppressWarnings("unchecked")
    private static OAuth2UserAttributes fromKakao(Map<String, Object> attributes) {
        Object id = attributes.get("id");
        if (id == null) {
            throw new ServiceErrorException(MemberExceptionEnum.ERR_SOCIAL_PROFILE_INCOMPLETE);
        }
        String providerId = String.valueOf(id);

        Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.getOrDefault("kakao_account", Map.of());
        String email = (String) kakaoAccount.get("email");

        Map<String, Object> profile = (Map<String, Object>) kakaoAccount.getOrDefault("profile", Map.of());
        String nickname = (String) profile.get("nickname");

        return validated(SocialProvider.KAKAO, providerId, email, nickname);
    }

    private static OAuth2UserAttributes fromGoogle(Map<String, Object> attributes) {
        String providerId = (String) attributes.get("sub");
        String email = (String) attributes.get("email");
        String name = (String) attributes.get("name");
        return validated(SocialProvider.GOOGLE, providerId, email, name);
    }

    private static OAuth2UserAttributes validated(SocialProvider provider, String providerId, String email, String name) {
        if (providerId == null || providerId.isBlank() || email == null || email.isBlank()) {
            throw new ServiceErrorException(MemberExceptionEnum.ERR_SOCIAL_PROFILE_INCOMPLETE);
        }
        return new OAuth2UserAttributes(provider, providerId, email, name);
    }
}
