package four_tential.potential.presentation.payment;

import four_tential.potential.application.payment.PaymentFacade;
import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.infra.portone.PortOneWebhookVerifier;
import io.portone.sdk.server.errors.WebhookVerificationException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PortOne 웹훅 수신 컨트롤러
 * PortOne 서버에서 결제 이벤트 발생 시 자동 호출됨
 * webhook-id / webhook-timestamp / webhook-signature 헤더로 검증
 */
@Slf4j
@Tag(name = "결제 웹훅", description = "PortOne 결제 웹훅 수신 · 서명 검증 · 멱등 처리 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/webhooks")
public class WebhookController {

    private final PaymentFacade paymentFacade;
    private final PortOneWebhookVerifier portOneWebhookVerifier;

    @Operation(
            summary = "PortOne 웹훅 수신",
            description = """
                    PortOne 서버가 호출하는 결제 웹훅 수신 API입니다.

                    - webhook-id, webhook-timestamp, webhook-signature를 이용해 PortOne 서명을 검증합니다.
                    - webhook-id를 먼저 저장해 같은 이벤트가 다시 들어와도 멱등하게 처리합니다.
                    - Paid / Failed / Cancelled 이벤트를 분기 처리합니다.
                    - Paid 이벤트는 pgKey lock, courseId lock, Payment FOR UPDATE 이후 최종 확정합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "웹훅 수신 및 처리 완료"),
            @ApiResponse(responseCode = "400", description = "필수 헤더 누락 또는 잘못된 요청 형식")
    })
    @PostMapping(value = "/portone", consumes = "application/json")
    public ResponseEntity<BaseResponse<Void>> receiveWebhook(
            @Parameter(description = "PortOne이 전달한 원본 웹훅 본문", required = true)
            @RequestBody String rawBody,
            @Parameter(description = "웹훅 멱등 처리용 고유 ID", required = true)
            @RequestHeader("webhook-id") String webhookId,
            @Parameter(description = "PortOne이 전달한 웹훅 전송 시각", required = true)
            @RequestHeader("webhook-timestamp") String webhookTimestamp,
            @Parameter(description = "PortOne 서명 검증용 헤더 값", required = true)
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

        return ResponseEntity.ok(BaseResponse.success("OK", "웹훅 처리가 완료되었습니다.", null));
    }
}
