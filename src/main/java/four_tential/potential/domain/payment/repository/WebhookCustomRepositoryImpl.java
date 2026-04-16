package four_tential.potential.domain.payment.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import four_tential.potential.domain.payment.entity.Webhook;
import four_tential.potential.domain.payment.enums.WebhookStatus;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

import static four_tential.potential.domain.payment.entity.QWebhook.webhook;

@RequiredArgsConstructor
public class WebhookCustomRepositoryImpl implements WebhookCustomRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public boolean existsByRecWebhookId(String recWebhookId) {
        Integer exists = queryFactory.selectOne()
                .from(webhook)
                .where(webhook.recWebhookId.eq(recWebhookId))
                .fetchFirst();

        return exists != null;
    }

    @Override
    public boolean existsCompletedByRecWebhookId(String recWebhookId) {
        Integer exists = queryFactory.selectOne()
                .from(webhook)
                .where(
                        webhook.recWebhookId.eq(recWebhookId),
                        webhook.status.eq(WebhookStatus.COMPLETED)
                )
                .fetchFirst();

        return exists != null;
    }

    @Override
    public Optional<Webhook> findByRecWebhookId(String recWebhookId) {
        return Optional.ofNullable(
                queryFactory.selectFrom(webhook)
                        .where(webhook.recWebhookId.eq(recWebhookId))
                        .fetchOne()
        );
    }
}
