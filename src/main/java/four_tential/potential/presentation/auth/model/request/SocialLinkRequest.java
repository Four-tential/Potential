package four_tential.potential.presentation.auth.model.request;

import jakarta.validation.constraints.NotBlank;

public record SocialLinkRequest(
        @NotBlank(message = "현재 비밀번호를 입력해주세요") String password,
        @NotBlank(message = "OAuth2 인가 코드가 필요합니다") String code,
        @NotBlank(message = "OAuth2 redirect URI 가 필요합니다") String redirectUri
) {
}
