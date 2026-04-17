package four_tential.potential.presentation.instructor_member.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ApplyInstructorRequest(
        @NotBlank(message = "카테고리 코드를 입력해주세요")
        @Size(max = 20, message = "카테고리 코드는 20자 이하로 입력해주세요")
        String categoryCode,

        @NotBlank(message = "지원 내용을 입력해주세요")
        String content,

        @NotBlank(message = "자격 증빙 이미지 URL을 입력해주세요")
        @Pattern(regexp = "^https?://[\\da-z.-]+\\.[a-z]{2,6}(/[\\w./-]*)?$", message = "올바른 URL 형식으로 입력해주세요")
        @Size(max = 300, message = "이미지 URL은 300자 이하로 입력해주세요")
        String imageUrl
) {
}
