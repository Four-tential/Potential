package four_tential.potential.presentation.auth.model.request;

import jakarta.validation.constraints.NotBlank;

public record SocialLinkConfirmRequest(
        @NotBlank(message = "challengeToken 이 필요합니다") String challengeToken,
        @NotBlank(message = "비밀번호를 입력해주세요") String password
) {
}
