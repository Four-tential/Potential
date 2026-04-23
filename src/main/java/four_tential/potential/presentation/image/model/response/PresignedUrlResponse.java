package four_tential.potential.presentation.image.model.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record PresignedUrlResponse(
        List<PresignedUrlItem> urls
) {
    public record PresignedUrlItem(
            @Schema(example = "https://example.aws.com/course-image/a1b2c3d4/uuid.jpg?X-Amz-Algorithm=...") String presignedUrl,
            @Schema(example = "https://cdn.example.com/course-image/a1b2c3d4/uuid.jpg") String imageUrl
    ) {
    }
}
