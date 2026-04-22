package four_tential.potential.presentation.payment;

import four_tential.potential.application.payment.PaymentFacade;
import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.infra.portone.PortOneWebhookVerifier;
import io.portone.sdk.server.errors.WebhookVerificationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * PortOne 웹훅 수신 컨트롤러
 * PortOne 서버에서 결제 이벤트 발생 시 자동 호출됨
 * webhook-id / webhook-timestamp / webhook-signature 헤더로 검증
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/webhooks")
public class WebhookController {

    private final PaymentFacade paymentFacade;
    private final PortOneWebhookVerifier portOneWebhookVerifier;

    @PostMapping(value = "/portone", consumes = "application/json")
    public ResponseEntity<BaseResponse<Void>> receiveWebhook(
            @RequestBody String rawBody,
            @RequestHeader("webhook-id") String webhookId,
            @RequestHeader("webhook-timestamp") String webhookTimestamp,
            @RequestHeader("webhook-signature") String webhookSignature) {

        log.info("[PORTONE_WEBHOOK] 웹훅 수신 id={} ts={}", webhookId, webhookTimestamp);

        try {
            io.portone.sdk.server.webhook.Webhook verified =
                    portOneWebhookVerifier.verify(rawBody, webhookId, webhookSignature, webhookTimestamp);
            paymentFacade.handleWebhook(rawBody, webhookId, verified);
        } catch (WebhookVerificationException e) {
            log.warn("[PORTONE_WEBHOOK] 서명 검증 실패. id={} reason={}", webhookId, e.getMessage());
            paymentFacade.handleInvalidWebhook(rawBody, webhookId, e.getMessage());
        }

        return ResponseEntity.ok(BaseResponse.success("OK", "웹훅 처리가 완료되었습니다", null));
    }
}
