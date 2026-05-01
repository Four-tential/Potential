CREATE TABLE refund_task
(
    id          BINARY(16)   NOT NULL,
    course_id   BINARY(16)   NOT NULL        COMMENT '원본 코스 ID',
    order_id    BINARY(16)   NOT NULL        COMMENT '환불 대상 주문 ID',
    payment_id  BINARY(16)   NOT NULL        COMMENT '환불 대상 결제 ID',
    member_id   BINARY(16)   NOT NULL        COMMENT '수강생 ID',
    status      VARCHAR(20)  NOT NULL        COMMENT 'PENDING / DONE / FAILED',
    fail_reason VARCHAR(500) NULL            COMMENT '실패 사유 (FAILED 시 기록)',
    created_at  DATETIME(6)  NOT NULL,
    update_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_refund_task_status (status),
    UNIQUE KEY uk_refund_task_order_id (order_id),
    INDEX idx_refund_task_course_id (course_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '주문별 환불 작업 테이블 - Job2가 읽어서 PortOne 환불을 처리한다';