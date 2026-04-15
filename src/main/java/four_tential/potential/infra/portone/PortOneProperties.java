package four_tential.potential.infra.portone;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * PortOne 설정값 프로퍼티
 * application.yaml 의 portone 하위 설정값을 주입받음
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "portone")
public class PortOneProperties {

    private String apiSecret;
    private String webhookSecret;
    private String storeId;
    private String channelKey;
}
