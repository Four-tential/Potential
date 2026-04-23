package four_tential.potential.infra.s3;

import four_tential.potential.common.exception.ServiceErrorException;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static four_tential.potential.common.exception.domain.MemberExceptionEnum.ERR_INVALID_IMAGE_FILE;

@Service
@RequiredArgsConstructor
public class S3Service {

    private static final Duration PRESIGNED_URL_EXPIRATION = Duration.ofMinutes(10);
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp"
    );
    private static final Map<String, String> CONTENT_TYPE_EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );

    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;

    public List<PresignedUrlResult> generatePresignedUrls(String prefix, @Nullable UUID resourceId, List<String> contentTypes) {
        return contentTypes.stream()
                .map(contentType -> {
                    validateContentType(contentType);
                    String extension = CONTENT_TYPE_EXTENSIONS.get(contentType);
                    String key = resourceId != null
                            ? "%s/%s/%s.%s".formatted(prefix, resourceId, UUID.randomUUID(), extension)
                            : "%s/%s.%s".formatted(prefix, UUID.randomUUID(), extension);
                    String presignedUrl = generatePresignedPutUrl(key, contentType);
                    String imageUrl = buildImageUrl(key);
                    return new PresignedUrlResult(presignedUrl, imageUrl);
                })
                .toList();
    }

    private String generatePresignedPutUrl(String key, String contentType) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(key)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(PRESIGNED_URL_EXPIRATION)
                .putObjectRequest(putObjectRequest)
                .build();

        return s3Presigner.presignPutObject(presignRequest).url().toString();
    }

    private void validateContentType(String contentType) {
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new ServiceErrorException(ERR_INVALID_IMAGE_FILE);
        }
    }

    private String buildImageUrl(String key) {
        return "https://%s/%s".formatted(s3Properties.getCloudfrontDomain(), key);
    }
}
