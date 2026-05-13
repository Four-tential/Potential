package four_tential.potential.infra.portone;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * PortOne 설정값 프로퍼티
 * application.yml 의 portone 하위 설정값을 바인딩한다
 */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "portone")
public class PortOneProperties {

    @NotBlank(message = "PortOne API secret is required.")
    private String apiSecret;

    @NotBlank(message = "PortOne webhook secret is required.")
    private String webhookSecret;

    @NotBlank(message = "PortOne store id is required.")
    private String storeId;

    @NotBlank(message = "PortOne channel key is required.")
    private String channelKey;

    @NotBlank(message = "PortOne API base is required.")
    private String apiBase;

    @NotNull(message = "PortOne SDK timeout is required.")
    private Duration sdkTimeout;
}
