package four_tential.potential.application.payment;

import four_tential.potential.application.payment.consts.PaymentWebhookConstants;
import four_tential.potential.domain.payment.entity.Webhook;
import four_tential.potential.domain.payment.repository.WebhookRepository;
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

    /**
     * PortOne 웹훅 수신 기록을 PENDING 상태로 저장한다.
     * 같은 webhook-id가 동시에 들어와 unique constraint가 발생하면 기존 row를 재조회해 멱등 처리한다.
     * REQUIRES_NEW: 이후 비즈니스 로직이 실패해 롤백되어도 수신 기록은 반드시 커밋된다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Webhook recordReceivedWebhook(String recWebhookId, String eventStatus, String payload) {
        try {
            return webhookRepository.saveAndFlush(Webhook.createPendingRecord(recWebhookId, eventStatus, payload));
        } catch (DataIntegrityViolationException e) {
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
     * 같은 webhook-id의 수신 기록이 이미 존재하는지 확인한다.
     */
    @Transactional(readOnly = true)
    public boolean isDuplicate(String recWebhookId) {
        return webhookRepository.existsByRecWebhookId(recWebhookId);
    }

    /**
     * webhook-id 기준으로 이미 성공 처리된 웹훅인지 확인한다.
     */
    @Transactional(readOnly = true)
    public boolean isCompleted(String recWebhookId) {
        return webhookRepository.existsCompletedByRecWebhookId(recWebhookId);
    }

    /**
     * webhook-id 기준으로 이미 종료된 웹훅인지 확인한다.
     * COMPLETED 또는 FAILED 라면 재전송되어도 더 이상 비즈니스 로직을 실행하지 않는다.
     */
    @Transactional(readOnly = true)
    public boolean isFinished(String recWebhookId) {
        return webhookRepository.existsFinishedByRecWebhookId(recWebhookId);
    }

    /**
     * 결제 생성 API보다 먼저 도착해 아직 완료되지 않은 Paid 웹훅을 조회한다.
     */
    @Transactional(readOnly = true)
    public Optional<Webhook> findProcessablePaidWebhook(String pgKey) {
        return webhookRepository.findLatestProcessableByPgKeyAndEventStatus(
                pgKey,
                PaymentWebhookConstants.WEBHOOK_TRANSACTION_PAID
        );
    }

    /**
     * 웹훅의 실제 이벤트 타입을 저장한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Webhook updateEventStatus(Webhook webhook, String eventStatus) {
        webhook.updateEventStatus(eventStatus);
        return webhookRepository.save(webhook);
    }

    /**
     * 실패 또는 보류 상태의 웹훅을 다시 처리 가능한 PENDING 상태로 되돌린다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Webhook markWebhookPendingForRetry(Webhook webhook, String eventStatus) {
        webhook.markPendingForRetry(eventStatus);
        return webhookRepository.save(webhook);
    }

    /**
     * Paid 웹훅이 payment 저장보다 먼저 도착했을 때 실패 처리하지 않고 보류한다.
     * 결제 생성 API가 payment를 저장하면 pgKey로 이 웹훅을 찾아 이어서 확정 처리한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Webhook deferPaidWebhookUntilPaymentSaved(Webhook webhook, String eventStatus, String pgKey) {
        webhook.markPendingForRetry(eventStatus);
        webhook.updatePgKey(pgKey);
        return webhookRepository.save(webhook);
    }

    /**
     * 결제 생성 검증 실패로 더 이상 이어갈 수 없는 선도착 Paid 웹훅을 실패 기록으로 마무리한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDeferredPaidWebhookFailure(String pgKey, String reason, String message) {
        findProcessablePaidWebhook(pgKey)
                .ifPresent(webhook -> recordFailedWebhook(webhook, reason, message));
    }

    /**
     * 웹훅 처리를 COMPLETED 상태로 마무리한다.
     * REQUIRES_NEW로 독립 저장해 비즈니스 처리 트랜잭션과 무관하게 완료 기록을 남긴다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordCompletedWebhook(Webhook webhook) {
        webhook.markCompleted();
        webhookRepository.save(webhook);
    }

    /**
     * 웹훅 처리를 FAILED 상태로 마무리한다.
     * REQUIRES_NEW로 독립 저장해 비즈니스 처리 트랜잭션이 롤백되어도 실패 기록을 남긴다.
     * webhook이 null이면 수신 자체가 실패한 것이므로 기록 없이 로그만 남기고 종료한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailedWebhook(Webhook webhook, String reason, String message) {
        if (webhook == null) {
            log.warn("[PORTONE_WEBHOOK] recordFailedWebhook() called with null webhook — 수신 기록 없음. reason={} message={}",
                    reason, message);
            return;
        }
        webhook.markFailed(reason, message);
        webhookRepository.save(webhook);
    }

    /**
     * detached 상태의 webhook을 현재 트랜잭션에 merge해 변경사항을 저장한다.
     * processTransactionWebhook처럼 기존 트랜잭션 안에서 호출해야 한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Webhook merge(Webhook webhook) {
        return webhookRepository.save(webhook);
    }
}
