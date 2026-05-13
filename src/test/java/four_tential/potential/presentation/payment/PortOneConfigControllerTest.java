package four_tential.potential.presentation.payment;

import four_tential.potential.infra.portone.PortOneProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PortOneConfigControllerTest {

    @InjectMocks
    private PortOneConfigController portOneConfigController;

    @Mock
    private PortOneProperties portOneProperties;

    @BeforeEach
    void setUp() {
        given(portOneProperties.getStoreId()).willReturn("store-test-id");
        given(portOneProperties.getChannelKey()).willReturn("channel-key-test");
    }

    @Test
    @DisplayName("getPortOneConfig 호출 시 200 OK 를 반환한다")
    void getPortOneConfig_returns_200() {
        ResponseEntity<?> response = portOneConfigController.getPortOneConfig();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("getPortOneConfig 호출 시 storeId 가 올바르게 반환된다")
    void getPortOneConfig_returns_storeId() {
        var response = portOneConfigController.getPortOneConfig();
        var body = response.getBody();

        assertThat(body).isNotNull();
        assertThat(body.toString()).contains("store-test-id");
    }

    @Test
    @DisplayName("getPortOneConfig 호출 시 channelKey 가 올바르게 반환된다")
    void getPortOneConfig_returns_channelKey() {
        var response = portOneConfigController.getPortOneConfig();
        var body = response.getBody();

        assertThat(body).isNotNull();
        assertThat(body.toString()).contains("channel-key-test");
    }
}