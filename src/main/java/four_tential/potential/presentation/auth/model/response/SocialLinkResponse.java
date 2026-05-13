package four_tential.potential.presentation.auth.model.response;

import four_tential.potential.domain.member.social.SocialProvider;
import io.swagger.v3.oas.annotations.media.Schema;

public record SocialLinkResponse(
        @Schema(description = "연동된 소셜 제공자", example = "KAKAO")
        SocialProvider provider,

        @Schema(description = "소셜에서 받은 이메일", example = "user@kakao.com")
        String email
) {
}
