ALTER TABLE refund_task
    ADD COLUMN next_retry_at DATETIME(6) NULL COMMENT '재시도 예정 시각 (RETRY_PENDING 상태에서만 사용)' AFTER status,
    ADD INDEX idx_refund_task_next_retry_at (next_retry_at);