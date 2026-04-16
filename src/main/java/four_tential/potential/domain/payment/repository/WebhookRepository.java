package four_tential.potential.domain.payment.repository;

import four_tential.potential.domain.payment.entity.Webhook;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WebhookRepository extends JpaRepository<Webhook, UUID> {

    boolean existsByRecWebhookId(String recWebhookId);

    Optional<Webhook> findByRecWebhookId(String recWebhookId);
}
