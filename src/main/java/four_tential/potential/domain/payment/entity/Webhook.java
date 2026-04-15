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

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public static Webhook receive(String recWebhookId, String eventStatus) {
        Webhook webhook = new Webhook();
        webhook.recWebhookId = recWebhookId;
        webhook.eventStatus = eventStatus;
        webhook.status = WebhookStatus.PENDING;
        webhook.receivedAt = LocalDateTime.now();
        return webhook;
    }

    /**
     * 웹훅 이벤트 상태 업데이트
     * SDK 검증 후 실제 이벤트 타입으로 업데이트
     *
     * @param eventStatus 실제 이벤트 타입
     */
    public void updateEventStatus(String eventStatus) {
        this.eventStatus = eventStatus;
    }

    /**
     * 웹훅 처리 완료
     */
    public void complete() {
        this.status = WebhookStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * 웹훅 처리 실패
     */
    public void fail() {
        this.status = WebhookStatus.FAILED;
        this.completedAt = LocalDateTime.now();
    }
}
