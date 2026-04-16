package four_tential.potential.domain.payment.entity;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.PaymentExceptionEnum;
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

    @Column(name = "pg_key", length = 300)
    private String pgKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WebhookStatus status;

    @Column(name = "event_status", nullable = false, length = 100)
    private String eventStatus;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // 웹훅 수신 기록 생성
    public static Webhook receive(String recWebhookId, String eventStatus) {
        Webhook webhook = new Webhook();
        webhook.recWebhookId = recWebhookId;
        webhook.eventStatus = eventStatus;
        webhook.status = WebhookStatus.PENDING;
        webhook.receivedAt = LocalDateTime.now();
        return webhook;
    }

    public void updateEventStatus(String eventStatus) {
        this.eventStatus = eventStatus;
    }

    public void updatePgKey(String pgKey) {
        this.pgKey = pgKey;
    }

    public boolean isCompleted() {
        return this.status == WebhookStatus.COMPLETED;
    }

    // 실패했던 웹훅을 다시 처리하기 위해 PENDING 상태로 만든다
    public void retry(String eventStatus) {
        transitTo(WebhookStatus.PENDING);
        this.eventStatus = eventStatus;
        this.completedAt = null;
        this.receivedAt = LocalDateTime.now();
    }

    public void complete() {
        if (transitTo(WebhookStatus.COMPLETED)) {
            this.completedAt = LocalDateTime.now();
        }
    }

    public void fail() {
        if (transitTo(WebhookStatus.FAILED)) {
            this.completedAt = LocalDateTime.now();
        }
    }

    private boolean transitTo(WebhookStatus target) {
        if (this.status == target) {
            return false;
        }
        if (!this.status.canTransitTo(target)) {
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_INVALID_WEBHOOK_STATUS_TRANSITION);
        }

        this.status = target;
        return true;
    }
}
