package four_tential.potential.presentation.auth.model.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record OAuthLoginExchangeResponse(
        @Schema(description = "JWT Access Token (Bearer)", example = "eyJhbGciOiJIUzI1NiJ9...")
        String accessToken,

        @Schema(description = "온보딩 완료 여부", example = "false")
        boolean hasOnboarding,

        @Schema(description = "전화번호 추가 입력 필요 여부 (소셜로 가입했지만 phone 미설정)", example = "true")
        boolean requiresPhoneSetup
) {
}
