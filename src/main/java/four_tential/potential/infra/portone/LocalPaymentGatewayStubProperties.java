package four_tential.potential.infra.portone;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "payment.gateway.stub")
public class LocalPaymentGatewayStubProperties {

    private boolean enabled = false;

    @NotNull
    private Duration getPaymentDelay = Duration.ofMillis(120);

    @NotNull
    private Duration cancelPaymentDelay = Duration.ofMillis(250);
}
