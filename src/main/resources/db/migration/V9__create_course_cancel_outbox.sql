CREATE TABLE course_cancel_outbox
(
    id          BINARY(16)   NOT NULL,
    course_id   BINARY(16)   NOT NULL        COMMENT '취소된 코스 ID',
    status      VARCHAR(20)  NOT NULL        COMMENT 'PENDING / DONE / FAILED',
    fail_reason VARCHAR(500) NULL            COMMENT '실패 사유',
    created_at  DATETIME(6)  NOT NULL,
    update_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_course_cancel_outbox_status (status),
    INDEX idx_course_cancel_outbox_course_id (course_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '강사 코스 취소 이벤트 아웃박스 - Job1이 읽어서 refund_task를 생성한다';