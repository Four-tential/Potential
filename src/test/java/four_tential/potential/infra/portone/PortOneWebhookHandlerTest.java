package four_tential.potential.infra.portone;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

class PortOneWebhookHandlerTest {

    private PortOneWebhookHandler portOneWebhookHandler;

    @BeforeEach
    void setUp() {
        portOneWebhookHandler = new PortOneWebhookHandler();
        ReflectionTestUtils.setField(portOneWebhookHandler, "webhookSecret", "test-webhook-secret");
    }

    @Test
    @DisplayName("verify 호출 시 UnsupportedOperationException 이 발생한다")
    void verify_throws_unsupported_operation_exception() {
        assertThatThrownBy(() -> portOneWebhookHandler.verify("payload", "signature"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("PortOne 웹훅 시크릿 준비 후 구현 예정");
    }

}