CREATE TABLE policy_document
(
    id            BINARY(16)   NOT NULL,
    source        VARCHAR(255) NOT NULL        COMMENT '정책 md 파일명 (.md 제외)',
    content_hash  VARCHAR(64)  NOT NULL        COMMENT '파일 본문 SHA-256 hex',
    chunk_count   INT          NOT NULL        COMMENT '적재된 청크 수',
    created_at    DATETIME(6)  NOT NULL,
    update_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_policy_document_source (source)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '정책 문서 적재 메타데이터 - 변경 감지/멱등 적재용';
