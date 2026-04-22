package four_tential.potential.infra.s3;

import four_tential.potential.common.exception.ServiceErrorException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class S3ServiceTest {

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private S3Properties s3Properties;

    @InjectMocks
    private S3Service s3Service;

    private void mockS3Dependencies() throws Exception {
        given(s3Properties.getBucket()).willReturn("test-bucket");
        given(s3Properties.getCloudfrontDomain()).willReturn("test.cloudfront.net");

        PresignedPutObjectRequest presignedRequest = mock(PresignedPutObjectRequest.class);
        given(presignedRequest.url()).willReturn(URI.create("https://s3.presigned.example.com/test").toURL());
        given(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).willReturn(presignedRequest);
    }

    @Test
    @DisplayName("resourceId가 있으면 prefix/resourceId/uuid.ext 형태의 키가 생성된다")
    void generatePresignedUrls_withResourceId() throws Exception {
        mockS3Dependencies();
        UUID resourceId = UUID.randomUUID();

        List<PresignedUrlResult> results = s3Service.generatePresignedUrls(
                "course-image", resourceId, List.of("image/jpeg")
        );

        assertThat(results).hasSize(1);
        assertThat(results.get(0).presignedUrl()).contains("s3.presigned");
        assertThat(results.get(0).imageUrl()).startsWith("https://test.cloudfront.net/course-image/" + resourceId + "/");
        assertThat(results.get(0).imageUrl()).endsWith(".jpg");
    }

    @Test
    @DisplayName("resourceId가 null이면 prefix/uuid.ext 형태의 키가 생성된다")
    void generatePresignedUrls_withoutResourceId() throws Exception {
        mockS3Dependencies();

        List<PresignedUrlResult> results = s3Service.generatePresignedUrls(
                "profile-image", null, List.of("image/png")
        );

        assertThat(results).hasSize(1);
        assertThat(results.get(0).imageUrl()).startsWith("https://test.cloudfront.net/profile-image/");
        assertThat(results.get(0).imageUrl()).endsWith(".png");
        assertThat(results.get(0).imageUrl()).doesNotContain("null");
    }

    @Test
    @DisplayName("여러 Content-Type을 전달하면 각각의 Presigned URL이 생성된다")
    void generatePresignedUrls_multipleContentTypes() throws Exception {
        mockS3Dependencies();
        UUID resourceId = UUID.randomUUID();

        List<PresignedUrlResult> results = s3Service.generatePresignedUrls(
                "course-image", resourceId, List.of("image/jpeg", "image/png", "image/webp")
        );

        assertThat(results).hasSize(3);
        assertThat(results.get(0).imageUrl()).endsWith(".jpg");
        assertThat(results.get(1).imageUrl()).endsWith(".png");
        assertThat(results.get(2).imageUrl()).endsWith(".webp");

        verify(s3Presigner, times(3)).presignPutObject(any(PutObjectPresignRequest.class));
    }

    @Test
    @DisplayName("허용되지 않은 Content-Type이면 예외가 발생한다")
    void generatePresignedUrls_invalidContentType() {
        assertThatThrownBy(() ->
                s3Service.generatePresignedUrls("course-image", UUID.randomUUID(), List.of("application/pdf"))
        ).isInstanceOf(ServiceErrorException.class);
    }

    @Test
    @DisplayName("허용된 Content-Type과 허용되지 않은 Content-Type이 섞이면 예외가 발생한다")
    void generatePresignedUrls_mixedContentTypes() throws Exception {
        mockS3Dependencies();

        assertThatThrownBy(() ->
                s3Service.generatePresignedUrls(
                        "course-image", UUID.randomUUID(),
                        List.of("image/jpeg", "text/plain")
                )
        ).isInstanceOf(ServiceErrorException.class);
    }

    @Test
    @DisplayName("S3Presigner에 올바른 bucket과 contentType이 전달된다")
    void generatePresignedUrls_correctPresignRequest() throws Exception {
        mockS3Dependencies();

        s3Service.generatePresignedUrls("profile-image", null, List.of("image/webp"));

        ArgumentCaptor<PutObjectPresignRequest> captor = ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        verify(s3Presigner).presignPutObject(captor.capture());

        PutObjectPresignRequest captured = captor.getValue();
        assertThat(captured.putObjectRequest().bucket()).isEqualTo("test-bucket");
        assertThat(captured.putObjectRequest().contentType()).isEqualTo("image/webp");
        assertThat(captured.putObjectRequest().key()).startsWith("profile-image/");
    }
}
