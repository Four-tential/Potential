package four_tential.potential.application.payment;

import four_tential.potential.domain.payment.entity.Webhook;
import four_tential.potential.domain.payment.port.PaymentGateway;
import four_tential.potential.infra.portone.PortOneWebhookHandler;
import io.portone.sdk.server.errors.WebhookVerificationException;
import io.portone.sdk.server.webhook.WebhookTransaction;
import io.portone.sdk.server.webhook.WebhookTransactionData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;


@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentFacade {
    private final PaymentService paymentService;
    private final WebhookService webhookService;
    private final PaymentGateway paymentGateway;
    private final PortOneWebhookHandler portOneWebhookHandler;
    private final TransactionTemplate transactionTemplate;


    /**
     * 웹훅 처리
     *
     * @param rawBody          웹훅 요청 본문
     * @param webhookId        webhook-id 헤더값
     * @param webhookTimestamp webhook-timestamp 헤더값
     * @param webhookSignature webhook-signature 헤더값
     * @throws WebhookVerificationException 서명 검증 실패 시
     */
    public void handleWebhook(
            String rawBody,
            String webhookId,
            String webhookTimestamp,
            String webhookSignature) throws WebhookVerificationException {

        // 멱등성 체크 - 이미 처리한 웹훅이면 무시
        if (webhookService.isDuplicate(webhookId)) {
            log.info("[PORTONE_WEBHOOK] 중복 웹훅 무시 id={}", webhookId);
            return;
        }

        // 트랜잭션 1 - 웹훅 수신 기록 저장
        // 이후 처리가 실패해도 수신 기록은 무조건 남음
        Webhook webhook = transactionTemplate.execute(
                status -> webhookService.receive(webhookId, "UNKNOWN"));

        // 서명 검증 (SDK 사용) - 트랜잭션 밖에서 수행
        // 검증 실패 시 WebhookVerificationException 발생 → Controller 에서 처리
        io.portone.sdk.server.webhook.Webhook verified =
                portOneWebhookHandler.verify(
                        rawBody, webhookId, webhookSignature, webhookTimestamp);

        // 트랜잭션 2 - 비즈니스 처리
        // 실패해도 트랜잭션 1 (웹훅 수신 기록) 은 롤백 안 됨
        try {
            transactionTemplate.execute(
                    new TransactionCallbackWithoutResult() {
                        @Override
                        protected void doInTransactionWithoutResult(
                                TransactionStatus status) {
                            processWebhook(verified, webhook);
                        }
                    });

        } catch (Exception e) {
            // 트랜잭션 2 실패 → REQUIRES_NEW 로 실패 상태 저장
            webhookService.fail(webhook);
            log.error("[PORTONE_WEBHOOK] 비즈니스 처리 실패 id={}", webhookId, e);
            throw e;
        }

        // 트랜잭션 2 성공 → REQUIRES_NEW 로 완료 상태 저장
        webhookService.complete(webhook);
    }

    /**
     * 웹훅 타입별 분기 처리
     *
     * @param verified SDK 검증 완료된 Webhook 객체
     * @param webhook  저장된 Webhook 엔티티
     */
    private void processWebhook(io.portone.sdk.server.webhook.Webhook verified, Webhook webhook) {

        if (verified instanceof WebhookTransaction transaction) {
            WebhookTransactionData data = transaction.getData();
            String paymentId = data.getPaymentId();
            String eventType = transaction.getClass().getSimpleName();

            log.info("[PORTONE_WEBHOOK] type={} paymentId={}",
                    eventType, paymentId);

            // 웹훅 이벤트 타입 업데이트
            webhook.updateEventStatus(eventType);

            switch (eventType) {
                case "WebhookTransactionPaid" ->
                        paymentService.confirmPaid(paymentId);
                case "WebhookTransactionFailed",
                     "WebhookTransactionCancelled" ->
                        paymentService.fail(paymentId);
                default ->
                        log.warn("[PORTONE_WEBHOOK] 처리되지 않은 타입: {}",
                                eventType);
            }
        }
    }

}
