package four_tential.potential.infra.portone;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * PortOne 설정값 프로퍼티
 * application.yaml 의 portone 하위 설정값을 주입받음
 */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "portone")
public class PortOneProperties {

    @NotBlank(message = "PortOne API Secret은 필수입니다")
    private String apiSecret;

    @NotBlank(message = "PortOne Webhook Secret은 필수입니다")
    private String webhookSecret;

    @NotBlank(message = "PortOne Store ID는 필수입니다")
    private String storeId;

    @NotBlank(message = "PortOne Channel Key는 필수입니다")
    private String channelKey;
}
