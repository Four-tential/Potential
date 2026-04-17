package four_tential.potential.application.payment;

import four_tential.potential.application.payment.consts.PaymentWebhookConstants;
import four_tential.potential.domain.payment.entity.Webhook;
import four_tential.potential.domain.payment.repository.WebhookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WebhookService {

    private final WebhookRepository webhookRepository;

    /**
     * PortOne 웹훅 수신 기록을 PENDING 상태로 저장한다.
     * 같은 webhook-id가 동시에 들어와 unique constraint가 발생하면 기존 row를 재조회해 멱등 처리한다.
     *
     * @param recWebhookId PortOne 웹훅 ID (webhook-id 헤더값)
     * @param eventStatus  초기 이벤트 상태
     * @return 저장된 Webhook 엔티티
     */
    public Webhook receive(String recWebhookId, String eventStatus) {
        try {
            return webhookRepository.saveAndFlush(Webhook.receive(recWebhookId, eventStatus));
        } catch (DataIntegrityViolationException e) {
            Webhook duplicated = webhookRepository.findByRecWebhookId(recWebhookId)
                    .orElseThrow(() -> e);
            if (!duplicated.isCompleted()) {
                duplicated.retry(eventStatus);
                return webhookRepository.save(duplicated);
            }
            return duplicated;
        }
    }

    /**
     * 같은 webhook-id의 수신 기록이 이미 존재하는지 확인한다.
     * 웹훅 멱등성 판단이 필요할 때 사용하는 단순 존재 여부 조회 메서드다.
     *
     * @param recWebhookId PortOne 웹훅 ID
     * @return 중복 여부
     */
    @Transactional(readOnly = true)
    public boolean isDuplicate(String recWebhookId) {
        return webhookRepository.existsByRecWebhookId(recWebhookId);
    }

    /**
     * webhook-id 기준으로 이미 성공 처리된 웹훅인지 확인한다.
     * COMPLETED라면 같은 웹훅 재전송으로 보고 이후 비즈니스 로직을 실행하지 않는다.
     */
    @Transactional(readOnly = true)
    public boolean isCompleted(String recWebhookId) {
        return webhookRepository.existsCompletedByRecWebhookId(recWebhookId);
    }

    /**
     * 결제 생성 API보다 먼저 도착해 아직 완료되지 않은 Paid 웹훅을 조회한다.
     * 결제 생성 직후 보류된 Paid 웹훅을 이어서 처리하기 위해 사용한다.
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
     * 결제 트랜잭션 이벤트가 아닌 웹훅도 UNKNOWN이 아닌 실제 타입으로 기록하기 위해 사용한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Webhook updateEventStatus(Webhook webhook, String eventStatus) {
        webhook.updateEventStatus(eventStatus);
        return webhookRepository.save(webhook);
    }

    /**
     * 실패 또는 보류 상태의 웹훅을 다시 처리 가능한 PENDING 상태로 되돌린다.
     * 기존 웹훅 row를 재사용하므로 webhook-id 멱등성을 유지한 채 재처리할 수 있다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Webhook retry(Webhook webhook, String eventStatus) {
        webhook.retry(eventStatus);
        return webhookRepository.save(webhook);
    }

    /**
     * Paid 웹훅이 payment 저장보다 먼저 도착했을 때 실패 처리하지 않고 보류한다.
     * 나중에 결제 생성 API가 payment를 저장하면 pgKey로 이 웹훅을 찾아 이어서 확정 처리한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Webhook defer(Webhook webhook, String eventStatus, String pgKey) {
        webhook.retry(eventStatus);
        webhook.updatePgKey(pgKey);
        return webhookRepository.save(webhook);
    }

    /**
     * 웹훅 처리를 COMPLETED 상태로 마무리한다.
     * REQUIRES_NEW로 독립 저장해 비즈니스 처리 트랜잭션과 무관하게 완료 기록을 남긴다.
     *
     * @param webhook 처리 완료할 Webhook 엔티티
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Webhook webhook) {
        webhook.complete();
        webhookRepository.save(webhook);
    }

    /**
     * 웹훅 처리를 FAILED 상태로 마무리한다.
     * REQUIRES_NEW로 독립 저장해 비즈니스 처리 트랜잭션이 롤백되어도 실패 기록을 남긴다.
     *
     * @param webhook 처리 실패한 Webhook 엔티티
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Webhook webhook) {
        webhook.fail();
        webhookRepository.save(webhook);
    }
}
