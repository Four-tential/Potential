package four_tential.potential.application.payment;

import four_tential.potential.application.payment.consts.PaymentWebhookConstants;
import four_tential.potential.domain.payment.entity.Webhook;
import four_tential.potential.domain.payment.repository.WebhookRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final WebhookRepository webhookRepository;
    private final EntityManager entityManager;

    /**
     * PortOne 웹훅 수신 기록을 먼저 남긴다
     * 처리 중 실패해도 원본 payload는 남아야 하므로 별도 트랜잭션으로 저장한다
     * 같은 webhook-id가 동시에 들어오면 기존 row를 다시 조회해 멱등하게 처리한다
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Webhook saveIncomingWebhook(String recWebhookId, String eventStatus, String payload) {
        try {
            return webhookRepository.saveAndFlush(Webhook.createPendingRecord(recWebhookId, eventStatus, payload));
        } catch (DataIntegrityViolationException e) {
            entityManager.clear();
            Webhook duplicated = webhookRepository.findByRecWebhookId(recWebhookId)
                    .orElseThrow(() -> e);
            if (duplicated.isFinished()) {
                return duplicated;
            }

            duplicated.markPendingForRetry(eventStatus);
            duplicated.updatePayload(payload);
            return webhookRepository.save(duplicated);
        }
    }

    /**
     * 이미 끝난 webhook-id인지 확인한다
     * COMPLETED 또는 FAILED면 재전송되어도 다시 처리하지 않는다
     */
    @Transactional(readOnly = true)
    public boolean isFinished(String recWebhookId) {
        return webhookRepository.existsFinishedByRecWebhookId(recWebhookId);
    }

    /**
     * 결제 생성보다 먼저 도착해 기다리는 Paid 웹훅을 찾는다
     */
    @Transactional(readOnly = true)
    public Optional<Webhook> findPendingPaidWebhook(String pgKey) {
        return webhookRepository.findLatestProcessableByPgKeyAndEventStatus(
                pgKey,
                PaymentWebhookConstants.WEBHOOK_TRANSACTION_PAID
        );
    }

    /**
     * UNKNOWN으로 저장했던 이벤트 타입을 실제 PortOne 이벤트 타입으로 바꾼다
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Webhook updateEventStatus(Webhook webhook, String eventStatus) {
        webhook.updateEventStatus(eventStatus);
        return webhookRepository.save(webhook);
    }

    /**
     * 다시 처리할 웹훅을 PENDING 상태로 돌려놓는다
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Webhook prepareRetry(Webhook webhook, String eventStatus) {
        webhook.markPendingForRetry(eventStatus);
        return webhookRepository.save(webhook);
    }

    /**
     * payment보다 먼저 온 Paid 웹훅을 잠시 보류한다
     * 순서 역전일 수 있으므로 FAILED로 끝내지 않고 pgKey를 남겨 둔다
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Webhook deferPaidWebhook(Webhook webhook, String eventStatus, String pgKey) {
        webhook.markPendingForRetry(eventStatus);
        webhook.updatePgKey(pgKey);
        return webhookRepository.save(webhook);
    }

    /**
     * 더 이상 이어갈 수 없는 보류 Paid 웹훅을 FAILED로 마무리한다
     * 실패 사유를 남겨 PortOne 재전송 없이 추적할 수 있게 한다
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failDeferredPaidWebhook(String pgKey, String reason, String message) {
        webhookRepository.findLatestProcessableByPgKeyAndEventStatus(
                        pgKey,
                        PaymentWebhookConstants.WEBHOOK_TRANSACTION_PAID
                )
                .ifPresent(webhook -> markFailedAndSave(webhook, reason, message));
    }

    /**
     * 웹훅을 COMPLETED 상태로 마무리한다
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeWebhook(Webhook webhook) {
        webhook.markCompleted();
        webhookRepository.save(webhook);
    }

    /**
     * 웹훅을 FAILED 상태로 마무리한다
     * 실패 사유와 메시지는 운영 확인용으로 함께 남긴다
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failWebhook(Webhook webhook, String reason, String message) {
        if (webhook == null) {
            log.warn("[PORTONE_WEBHOOK] failWebhook() called with null webhook — 수신 기록 없음. reason={} message={}",
                    reason, message);
            return;
        }
        markFailedAndSave(webhook, reason, message);
    }

    private void markFailedAndSave(Webhook webhook, String reason, String message) {
        webhook.markFailed(reason, message);
        webhookRepository.save(webhook);
    }

    /**
     * 현재 비즈니스 트랜잭션 안에서 webhook 변경사항을 저장한다
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Webhook merge(Webhook webhook) {
        return webhookRepository.save(webhook);
    }
}
