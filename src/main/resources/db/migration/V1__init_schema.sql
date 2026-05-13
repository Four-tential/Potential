CREATE TABLE members
(
    id             BINARY(16)   NOT NULL,
    email          VARCHAR(100) NOT NULL,
    password       VARCHAR(100) NOT NULL,
    phone          VARCHAR(40)  NOT NULL,
    role           VARCHAR(30)  NOT NULL,
    status         VARCHAR(30)  NOT NULL,
    name           VARCHAR(60)  NOT NULL,
    has_onboarding BIT          NOT NULL,
    pf_image_url   VARCHAR(300),
    withdrawal_at  DATETIME(6),
    created_at     DATETIME(6)  NOT NULL,
    update_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_members_email UNIQUE (email)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE instructor_members
(
    id            BINARY(16)   NOT NULL,
    member_id     BINARY(16)   NOT NULL,
    category_code VARCHAR(20)  NOT NULL,
    status        VARCHAR(30)  NOT NULL,
    content       TEXT         NOT NULL,
    image_url     VARCHAR(300) NOT NULL,
    reject_reason TEXT,
    approved_at   DATETIME(6),
    responded_at  DATETIME(6),
    created_at    DATETIME(6)  NOT NULL,
    update_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_instructor_member_member_id UNIQUE (member_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE follows
(
    id                   BINARY(16)  NOT NULL,
    member_id            BINARY(16)  NOT NULL,
    member_instructor_id BINARY(16)  NOT NULL,
    created_at           DATETIME(6) NOT NULL,
    update_at            DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_follows_member_instructor UNIQUE (member_id, member_instructor_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE member_onboards
(
    id         BINARY(16)  NOT NULL,
    member_id  BINARY(16)  NOT NULL,
    goal       VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    update_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_member_onboards_member_id UNIQUE (member_id),
    CONSTRAINT fk_member_onboards_member FOREIGN KEY (member_id) REFERENCES members (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE member_onboard_categories
(
    id            BINARY(16)  NOT NULL,
    member_id     BINARY(16)  NOT NULL,
    category_code VARCHAR(30) NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    update_at     DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_member_onboard_categories_member_category UNIQUE (member_id, category_code),
    CONSTRAINT fk_member_onboard_categories_member FOREIGN KEY (member_id) REFERENCES members (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE course_categories
(
    id         BINARY(16)  NOT NULL,
    code       VARCHAR(20) NOT NULL,
    name       VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    update_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_course_categories_code UNIQUE (code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE courses
(
    id                   BINARY(16)   NOT NULL,
    course_category_id   BINARY(16)   NOT NULL,
    member_instructor_id BINARY(16)   NOT NULL,
    title                VARCHAR(100) NOT NULL,
    description          TEXT         NOT NULL,
    address_main         VARCHAR(300) NOT NULL,
    address_detail       VARCHAR(300) NOT NULL,
    capacity             INT          NOT NULL,
    confirm_count        INT          NOT NULL,
    price                DECIMAL(38,0) NOT NULL,
    level                VARCHAR(30)  NOT NULL,
    status               VARCHAR(30)  NOT NULL,
    order_open_at        DATETIME(6)  NOT NULL,
    order_close_at       DATETIME(6)  NOT NULL,
    start_at             DATETIME(6)  NOT NULL,
    end_at               DATETIME(6)  NOT NULL,
    confirmed_at         DATETIME(6),
    reject_reason        TEXT,
    deleted              BIT          NOT NULL,
    deleted_at           DATETIME(6),
    created_at           DATETIME(6)  NOT NULL,
    update_at            DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE course_images
(
    id         BINARY(16)   NOT NULL,
    course_id  BINARY(16)   NOT NULL,
    image_url  VARCHAR(300) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    update_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_course_images_course FOREIGN KEY (course_id) REFERENCES courses (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE course_wishlists
(
    id         BINARY(16)  NOT NULL,
    member_id  BINARY(16)  NOT NULL,
    course_id  BINARY(16)  NOT NULL,
    created_at DATETIME(6) NOT NULL,
    update_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_course_wishlists_member_course UNIQUE (member_id, course_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE course_approval_histories
(
    id            BINARY(16)  NOT NULL,
    course_id     BINARY(16)  NOT NULL,
    action        VARCHAR(10) NOT NULL,
    reject_reason VARCHAR(255),
    created_at    DATETIME(6) NOT NULL,
    update_at     DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE orders
(
    id               BINARY(16)   NOT NULL,
    course_id        BINARY(16)   NOT NULL,
    member_id        BINARY(16)   NOT NULL,
    order_count      INT          NOT NULL,
    price_snap       DECIMAL(38,0) NOT NULL,
    total_price_snap DECIMAL(38,0) NOT NULL,
    title_snap       VARCHAR(100) NOT NULL,
    status           VARCHAR(30)  NOT NULL,
    cancelled_at     DATETIME(6),
    expire_at        DATETIME(6)  NOT NULL,
    version          BIGINT,
    created_at       DATETIME(6)  NOT NULL,
    update_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE waiting_list
(
    id          BINARY(16)  NOT NULL,
    course_id   BINARY(16)  NOT NULL,
    member_id   BINARY(16)  NOT NULL,
    wait_number INT         NOT NULL,
    status      VARCHAR(30) NOT NULL,
    waited_at   DATETIME(6) NOT NULL,
    called_at   DATETIME(6),
    expired_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE attendances
(
    id            BINARY(16)  NOT NULL,
    order_id      BINARY(16)  NOT NULL,
    member_id     BINARY(16)  NOT NULL,
    course_id     BINARY(16)  NOT NULL,
    qr_code       VARCHAR(300),
    status        VARCHAR(30) NOT NULL,
    attendance_at DATETIME(6),
    created_at    DATETIME(6) NOT NULL,
    update_at     DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_attendances_qr_code UNIQUE (qr_code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE payments
(
    id               BINARY(16)  NOT NULL,
    order_id         BINARY(16)  NOT NULL,
    member_id        BINARY(16)  NOT NULL,
    pg_key           VARCHAR(300),
    total_price      BIGINT      NOT NULL,
    paid_total_price BIGINT      NOT NULL,
    pay_way          VARCHAR(30) NOT NULL,
    status           VARCHAR(30) NOT NULL,
    paid_at          DATETIME(6),
    created_at       DATETIME(6) NOT NULL,
    update_at        DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payments_order_id UNIQUE (order_id),
    CONSTRAINT uk_payments_pg_key UNIQUE (pg_key)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE refunds
(
    id           BINARY(16)  NOT NULL,
    payment_id   BINARY(16)  NOT NULL,
    refund_price BIGINT      NOT NULL,
    cancel_count INT         NOT NULL,
    reason       VARCHAR(30) NOT NULL,
    status       VARCHAR(30) NOT NULL,
    refunded_at  DATETIME(6),
    created_at   DATETIME(6) NOT NULL,
    update_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_refunds_payment FOREIGN KEY (payment_id) REFERENCES payments (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE webhooks
(
    id             BINARY(16)   NOT NULL,
    rec_webhook_id VARCHAR(500) NOT NULL,
    pg_key         VARCHAR(300),
    status         VARCHAR(30)  NOT NULL,
    event_status   VARCHAR(100) NOT NULL,
    payload        LONGTEXT,
    fail_reason    VARCHAR(100),
    fail_message   VARCHAR(1000),
    received_at    DATETIME(6)  NOT NULL,
    completed_at   DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_webhooks_rec_webhook_id UNIQUE (rec_webhook_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE reviews
(
    id         BINARY(16)  NOT NULL,
    member_id  BINARY(16)  NOT NULL,
    course_id  BINARY(16)  NOT NULL,
    order_id   BINARY(16)  NOT NULL,
    rating     INT         NOT NULL,
    content    TEXT        NOT NULL,
    created_at DATETIME(6) NOT NULL,
    update_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE review_images
(
    id         BINARY(16)   NOT NULL,
    reviews_id BINARY(16)   NOT NULL,
    image_url  VARCHAR(300) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    update_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_review_images_review FOREIGN KEY (reviews_id) REFERENCES reviews (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE review_likes
(
    id         BINARY(16)  NOT NULL,
    reviews_id BINARY(16)  NOT NULL,
    member_id  BINARY(16)  NOT NULL,
    created_at DATETIME(6) NOT NULL,
    update_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_review_likes_review_member UNIQUE (reviews_id, member_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE coupon_policies
(
    id                 BINARY(16)   NOT NULL,
    name               VARCHAR(300) NOT NULL,
    discount_type      VARCHAR(30)  NOT NULL,
    discount_price     BIGINT       NOT NULL,
    min_pay_price      BIGINT       NOT NULL,
    max_discount_price BIGINT,
    total_quantity     BIGINT,
    issued_quantity    BIGINT,
    start_dt           DATETIME(6)  NOT NULL,
    end_dt             DATETIME(6)  NOT NULL,
    created_at         DATETIME(6)  NOT NULL,
    update_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE member_coupons
(
    id               BINARY(16)  NOT NULL,
    coupon_policy_id BINARY(16)  NOT NULL,
    member_id        BINARY(16)  NOT NULL,
    status           VARCHAR(30) NOT NULL,
    issued_at        DATETIME(6) NOT NULL,
    created_at       DATETIME(6) NOT NULL,
    update_at        DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_member_coupons_coupon_policy FOREIGN KEY (coupon_policy_id) REFERENCES coupon_policies (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
