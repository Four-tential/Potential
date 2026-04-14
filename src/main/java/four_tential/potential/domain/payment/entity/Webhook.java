package four_tential.potential.domain.payment.entity;

import four_tential.potential.domain.payment.enums.WebhookStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "webhooks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Webhook {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "rec_webhook_id", nullable = false, unique = true, length = 500)
    private String recWebhookId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WebhookStatus status;

    @Column(name = "event_status", nullable = false, length = 100)
    private String eventStatus;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static Webhook receive(String recWebhookId, String eventStatus) {
        Webhook webhook = new Webhook();
        webhook.recWebhookId = recWebhookId;
        webhook.eventStatus = eventStatus;
        webhook.status = WebhookStatus.PENDING;
        webhook.recivedAt = LocalDateTime.now();
        webhook.createdAt = LocalDateTime.now();
        return webhook;
    }
}
