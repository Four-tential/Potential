package four_tential.potential.presentation.auth.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignUpRequest(
        @NotBlank(message = "이메일은 필수 입니다")
        @Email(message = "이메일 형식이 아닙니다")
        @Size(max = 100, message = "이메일은 최대 100글자 입니다")
        String email,

        @NotBlank(message = "비밀번호는 필수 입니다")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{12,}$",
                message = "비밀번호는 12자 이상이며, 영문 대/소문자, 숫자, 특수문자를 각각 1개 이상 포함해야 합니다"
        )
        String password,

        @NotBlank(message = "이름은 필수 입니다")
        @Size(max = 60, message = "이름은 최대 60글자 입니다")
        String name,

        @NotBlank(message = "휴대전화 번호는 필수 입니다")
        @Pattern(
                regexp = "^01[016789]-\\d{3,4}-\\d{4}$",
                message = "올바른 휴대전화 번호 형식으로 입력해주세요 (- 포함)"
        )
        String phone
) {
}
