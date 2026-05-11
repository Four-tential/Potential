package four_tential.potential.presentation.auth.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record SocialLinkRequest(
        @Schema(description = "현재 로그인 회원의 비밀번호", example = "P@ssw0rd!", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "현재 비밀번호를 입력해주세요") String password,

        @Schema(description = "OAuth2 인가 코드 (프론트가 provider 로부터 받은 code)", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "OAuth2 인가 코드가 필요합니다") String code,

        @Schema(description = "프론트에서 인가 요청 시 사용한 redirect_uri (provider 검증용)",
                example = "https://app.example.com/oauth/callback", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "OAuth2 redirect URI 가 필요합니다") String redirectUri
) {
}
