package four_tential.potential.domain.payment.repository;

import four_tential.potential.domain.payment.entity.Webhook;

import java.util.Optional;

public interface WebhookCustomRepository {

    boolean existsByRecWebhookId(String recWebhookId);

    boolean existsCompletedByRecWebhookId(String recWebhookId);

    Optional<Webhook> findByRecWebhookId(String recWebhookId);
}
