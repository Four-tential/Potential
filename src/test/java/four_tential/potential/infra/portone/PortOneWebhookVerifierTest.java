package four_tential.potential.infra.portone;

import io.portone.sdk.server.errors.WebhookVerificationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

class PortOneWebhookVerifierTest {

    private PortOneWebhookVerifier portOneWebhookVerifier;

    @BeforeEach
    void setUp() {
        PortOneProperties properties = Mockito.mock(PortOneProperties.class);
        Mockito.when(properties.getWebhookSecret())
                .thenReturn("whsec_dGVzdC13ZWJob29rLXNlY3JldA==");

        portOneWebhookVerifier = new PortOneWebhookVerifier(properties);
        portOneWebhookVerifier.init();
    }

    @Test
    @DisplayName("잘못된 서명으로 verify 호출 시 WebhookVerificationException 이 발생한다")
    void verify_throws_when_invalid_signature() {
        assertThatThrownBy(() ->
                portOneWebhookVerifier.verify(
                        "{\"type\":\"Transaction.Paid\"}",
                        "test-webhook-id",
                        "v1,invalidsignature",
                        "1234567890"
                ))
                .isInstanceOf(WebhookVerificationException.class);
    }
}