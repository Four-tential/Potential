package four_tential.potential.presentation.auth.model.response;

import four_tential.potential.domain.member.social.SocialProvider;
import io.swagger.v3.oas.annotations.media.Schema;

public record OAuthLinkExchangeResponse(
        @Schema(description = "비밀번호 검증 confirm 단계에서 사용할 챌린지 토큰 (TTL 5분)", example = "ch_8f3c2a4d7b9e")
        String challengeToken,

        @Schema(description = "충돌이 발생한 기존 계정의 이메일", example = "user@kakao.com")
        String email,

        @Schema(description = "연동을 시도한 소셜 제공자", example = "KAKAO")
        SocialProvider provider
) {
}
