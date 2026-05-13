package four_tential.potential.infra.s3;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "aws.s3")
public class S3Properties {

    @NotBlank(message = "S3 버킷 이름은 필수입니다")
    private String bucket;

    @NotBlank(message = "S3 리전은 필수입니다")
    private String region;

    @NotBlank(message = "CloudFront 도메인은 필수입니다")
    private String cloudfrontDomain;
}
