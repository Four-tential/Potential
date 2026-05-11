package four_tential.potential.presentation.auth.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record SocialLinkConfirmRequest(
        @Schema(description = "link-ticket/exchange 응답에서 받은 challengeToken (TTL 5분)",
                example = "ch_8f3c2a4d7b9e", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "challengeToken 이 필요합니다") String challengeToken,

        @Schema(description = "충돌한 기존 계정의 비밀번호", example = "P@ssw0rd!", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "비밀번호를 입력해주세요") String password
) {
}
