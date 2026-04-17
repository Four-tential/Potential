package four_tential.potential.presentation.payment;

import four_tential.potential.application.payment.PaymentFacade;
import four_tential.potential.common.dto.BaseResponse;
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
@RequestMapping("/api/v1/webhooks")
public class WebhookController {

    private final PaymentFacade paymentFacade;

    @PostMapping(value = "/portone", consumes = "application/json")
    public ResponseEntity<BaseResponse<Void>> receiveWebhook(
            @RequestBody String rawBody,
            @RequestHeader("webhook-id") String webhookId,
            @RequestHeader("webhook-timestamp") String webhookTimestamp,
            @RequestHeader("webhook-signature") String webhookSignature) {

        log.info("[PORTONE_WEBHOOK] 웹훅 수신 id={} ts={}", webhookId, webhookTimestamp);

        paymentFacade.handleWebhook(rawBody, webhookId, webhookTimestamp, webhookSignature);

        return ResponseEntity.ok(BaseResponse.success("OK", "웹훅 처리가 완료되었습니다", null));
    }
}
