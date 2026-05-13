package four_tential.potential.presentation.auth.model.response;

import four_tential.potential.domain.member.social.SocialProvider;
import io.swagger.v3.oas.annotations.media.Schema;

public record SocialLinkConfirmResponse(
        @Schema(description = "JWT Access Token", example = "eyJhbGciOiJIUzI1NiJ9...")
        String accessToken,

        @Schema(description = "온보딩 완료 여부", example = "true")
        boolean hasOnboarding,

        @Schema(description = "전화번호 추가 입력 필요 여부", example = "false")
        boolean requiresPhoneSetup,

        @Schema(description = "방금 연동된 소셜 제공자", example = "KAKAO")
        SocialProvider linkedProvider,

        @Schema(description = "회원 이메일", example = "user@example.com")
        String email
) {
}
