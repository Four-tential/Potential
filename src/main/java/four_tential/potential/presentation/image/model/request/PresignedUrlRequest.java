package four_tential.potential.presentation.image.model.request;

import four_tential.potential.infra.s3.ImageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record PresignedUrlRequest(
        @NotNull(message = "이미지 용도를 입력해주세요")
        ImageType type,

        UUID resourceId,

        @NotEmpty(message = "Content-Type 목록을 입력해주세요")
        @Size(max = 10, message = "한 번에 최대 10개까지 업로드할 수 있습니다")
        List<@NotBlank(message = "Content-Type은 빈 값일 수 없습니다") String> contentTypes
) {
}
