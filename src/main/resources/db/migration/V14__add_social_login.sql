-- 소셜 전용 사용자는 password / phone 이 가입 시점에 없을 수 있음
ALTER TABLE members MODIFY COLUMN password VARCHAR(100) NULL;
ALTER TABLE members MODIFY COLUMN phone    VARCHAR(40)  NULL;

CREATE TABLE member_social_accounts
(
    id          BINARY(16)   NOT NULL,
    member_id   BINARY(16)   NOT NULL,
    provider    VARCHAR(30)  NOT NULL                   COMMENT 'KAKAO | GOOGLE',
    provider_id VARCHAR(255) NOT NULL                   COMMENT 'OAuth2 provider 측 사용자 식별자',
    email       VARCHAR(255)                            COMMENT '연동 시점의 provider 측 이메일 스냅샷',
    created_at  DATETIME(6)  NOT NULL,
    update_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_msa_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT uk_msa_provider_provider_id UNIQUE (provider, provider_id),
    CONSTRAINT uk_msa_member_provider UNIQUE (member_id, provider)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '소셜 로그인 연동 계정';
