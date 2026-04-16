package four_tential.potential.presentation.member.model.request;

import jakarta.validation.constraints.Pattern;

public record UpdateMyPageRequest(
        @Pattern(regexp = "^01[016789]-\\d{3,4}-\\d{4}$", message = "올바른 휴대전화 번호 형식으로 입력해주세요 (- 포함)")
        String phone,

        String profileImageUrl
) {
}
