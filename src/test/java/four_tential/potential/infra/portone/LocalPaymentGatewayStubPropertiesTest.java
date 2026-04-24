package four_tential.potential.infra.portone;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LocalPaymentGatewayStubPropertiesTest {

    @Test
    @DisplayName("기본값은 비활성화이며 조회/취소 지연 시간이 설정되어 있다")
    void default_values() {
        LocalPaymentGatewayStubProperties properties = new LocalPaymentGatewayStubProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getGetPaymentDelay()).isEqualTo(Duration.ofMillis(120));
        assertThat(properties.getCancelPaymentDelay()).isEqualTo(Duration.ofMillis(250));
    }

    @Test
    @DisplayName("setter로 enabled와 지연 시간을 변경할 수 있다")
    void setters_update_values() {
        LocalPaymentGatewayStubProperties properties = new LocalPaymentGatewayStubProperties();

        properties.setEnabled(true);
        properties.setGetPaymentDelay(Duration.ofMillis(321));
        properties.setCancelPaymentDelay(Duration.ofMillis(654));

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getGetPaymentDelay()).isEqualTo(Duration.ofMillis(321));
        assertThat(properties.getCancelPaymentDelay()).isEqualTo(Duration.ofMillis(654));
    }
}
