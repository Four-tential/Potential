CREATE INDEX idx_payments_member_id_created_at
    ON payments (member_id, created_at);

CREATE INDEX idx_payments_member_id_status_created_at
    ON payments (member_id, status, created_at);

CREATE INDEX idx_refunds_payment_id_status
    ON refunds (payment_id, status);

CREATE INDEX idx_webhooks_pg_key_event_status_status_received_at
    ON webhooks (pg_key, event_status, status, received_at);