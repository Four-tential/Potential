package four_tential.potential.infra.s3;

public record PresignedUrlResult(
        String presignedUrl,
        String imageUrl
) {
}
