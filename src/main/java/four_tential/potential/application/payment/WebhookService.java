package four_tential.potential.application.payment;

import four_tential.potential.domain.payment.entity.Webhook;
import four_tential.potential.domain.payment.repository.WebhookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WebhookService {

    private final WebhookRepository webhookRepository;

    /**
     * 웹훅 수신 저장
     * 웹훅 수신 즉시 PENDING 상태로 저장
     *
     * @param recWebhookId PortOne 웹훅 ID (webhook-id 헤더값)
     * @param eventStatus  초기 이벤트 상태
     * @return 저장된 Webhook 엔티티
     */
    @Transactional
    public Webhook receive(String recWebhookId, String eventStatus) {
        Webhook webhook = Webhook.receive(recWebhookId, eventStatus);
        return webhookRepository.save(webhook);
    }

    /**
     * 웹훅 중복 여부 확인 (멱등성 체크)
     *
     * @param recWebhookId PortOne 웹훅 ID
     * @return 중복 여부
     */
    public boolean isDuplicate(String recWebhookId) {
        return webhookRepository.existsByRecWebhookId(recWebhookId);
    }

    /**
     * 웹훅 처리 완료 처리
     * REQUIRES_NEW 로 독립 트랜잭션 실행
     * 비즈니스 처리 트랜잭션과 무관하게 완료 상태 저장
     *
     * @param webhook 처리 완료할 Webhook 엔티티
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Webhook webhook) {
        webhook.complete();
        webhookRepository.save(webhook);
    }

    /**
     * 웹훅 처리 실패 처리
     * REQUIRES_NEW 로 독립 트랜잭션 실행
     * 비즈니스 처리 트랜잭션이 롤백되어도 실패 상태 저장
     *
     * @param webhook 처리 실패한 Webhook 엔티티
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Webhook webhook) {
        webhook.fail();
        webhookRepository.save(webhook);
    }
}
