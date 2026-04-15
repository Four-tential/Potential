package four_tential.potential.presentation.payment;

import four_tential.potential.application.payment.PaymentFacade;
import four_tential.potential.common.dto.BaseResponse;
import io.portone.sdk.server.errors.WebhookVerificationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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

    /**
     * PortOne 웹훅 수신
     *
     * @param rawBody          웹훅 요청 본문 (JSON 원문, 서명 검증에 사용)
     * @param webhookId        webhook-id 헤더값 (멱등성 키)
     * @param webhookTimestamp webhook-timestamp 헤더값 (리플레이 공격 방지)
     * @param webhookSignature webhook-signature 헤더값 (서명값)
     * @return 처리 결과
     */
    @PostMapping(value = "/portone", consumes = "application/json")
    public ResponseEntity<BaseResponse<Void>> receiveWebhook(
            @RequestBody String rawBody,
            @RequestHeader("webhook-id") String webhookId,
            @RequestHeader("webhook-timestamp") String webhookTimestamp,
            @RequestHeader("webhook-signature") String webhookSignature) {

        log.info("[PORTONE_WEBHOOK] 웹훅 수신 id={} ts={}", webhookId, webhookTimestamp);

        try {
            paymentFacade.handleWebhook(rawBody, webhookId, webhookTimestamp, webhookSignature);

        } catch (WebhookVerificationException e) {
            log.warn("[PORTONE_WEBHOOK] 서명 검증 실패 id={}", webhookId);
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(BaseResponse.fail(HttpStatus.UNAUTHORIZED.name(), "웹훅 서명 검증에 실패했습니다"));
        }

        return ResponseEntity.ok(BaseResponse.success("OK", "웹훅 처리가 완료되었습니다", null));
    }
}
