package four_tential.potential.presentation.course.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCourseCategoryRequest(
        @NotBlank(message = "카테고리 코드를 입력해주세요")
        @Size(max = 20, message = "카테고리 코드는 20자 이하로 입력해주세요")
        String code,

        @NotBlank(message = "카테고리 이름을 입력해주세요")
        @Size(max = 50, message = "카테고리 이름은 50자 이하로 입력해주세요")
        String name
) {
}
