package four_tential.potential.infra.portone;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PortOneClientTest {

    @Test
    @DisplayName("PortOneProperties 로 PortOneClient 를 생성할 수 있다")
    void createClient_with_properties() {
        PortOneProperties properties = new PortOneProperties();
        properties.setApiSecret("test-api-secret");
        properties.setStoreId("store-test");

        PortOneClient portOneClient = new PortOneClient(properties);

        assertThat(portOneClient).isNotNull();
    }
}
