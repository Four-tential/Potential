package four_tential.potential.presentation.member.model.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateMyPageRequest(
        @Pattern(regexp = "^01[016789]-\\d{3,4}-\\d{4}$", message = "올바른 휴대전화 번호 형식으로 입력해주세요 (- 포함)")
        String phone,

        @Size(max = 500, message = "프로필 이미지 URL은 500자 이하로 입력해주세요")
        String profileImageUrl
) {
}
