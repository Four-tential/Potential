package four_tential.potential.infra.portone;

import io.portone.sdk.server.errors.WebhookVerificationException;
import io.portone.sdk.server.webhook.Webhook;
import io.portone.sdk.server.webhook.WebhookVerifier;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * PortOne 웹훅 서명 검증 핸들러
 * PortOne 공식 Server SDK 를 이용하여 웹훅 서명을 검증
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PortOneWebhookVerifier {

    private final PortOneProperties portOneProperties;
    private WebhookVerifier webhookVerifier;

    @PostConstruct
    void init() {
        this.webhookVerifier = new WebhookVerifier(
                portOneProperties.getWebhookSecret());
        log.info("[PortOneWebhookHandler] WebhookVerifier 초기화 완료");
    }

    /**
     * 웹훅 서명 검증
     * SDK가 서명 검증과 역직렬화를 동시에 처리
     *
     * @param rawBody        웹훅 요청 본문 (JSON 원문)
     * @param webhookId      webhook-id 헤더값
     * @param webhookSignature webhook-signature 헤더값
     * @param webhookTimestamp webhook-timestamp 헤더값
     * @return 검증 및 역직렬화된 Webhook 객체
     * @throws WebhookVerificationException 서명 검증 실패 시
     */
    public Webhook verify(
            String rawBody,
            String webhookId,
            String webhookSignature,
            String webhookTimestamp) throws WebhookVerificationException {
        return webhookVerifier.verify(
                rawBody, webhookId, webhookSignature, webhookTimestamp);
    }


}
