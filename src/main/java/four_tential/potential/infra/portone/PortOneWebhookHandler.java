package four_tential.potential.infra.portone;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PortOneWebhookHandler {

    @Value("${portone.webhook-secret}")
    private String webhookSecret;

    // TODO: PortOne 웹훅 서명 검증 구현 예정
    public boolean verify(String payload, String signature) {
        throw new UnsupportedOperationException("PortOne 웹훅 시크릿 준비 후 구현 예정");
    }


}
