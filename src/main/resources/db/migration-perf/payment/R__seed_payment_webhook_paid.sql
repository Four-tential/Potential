-- Performance fixture seed for Paid webhook scenario
-- login password: testTEST123!@#

SET SESSION cte_max_recursion_depth = 1000;

-- ---------------------------------------------------------------------------
-- Cleanup previous performance fixtures
-- ---------------------------------------------------------------------------
DELETE FROM webhooks
WHERE rec_webhook_id LIKE 'perf-paid-%';

DELETE r
FROM refunds r
         JOIN payments p ON r.payment_id = p.id
WHERE p.member_id = UUID_TO_BIN('00000000-0000-0000-0000-000000009944');

DELETE FROM payments
WHERE member_id = UUID_TO_BIN('00000000-0000-0000-0000-000000009944');

DELETE FROM orders
WHERE member_id = UUID_TO_BIN('00000000-0000-0000-0000-000000009944');

DELETE ci
FROM course_images ci
         JOIN courses c ON ci.course_id = c.id
WHERE c.member_instructor_id = UUID_TO_BIN('00000000-0000-0000-0000-000000009943');

DELETE FROM courses
WHERE member_instructor_id = UUID_TO_BIN('00000000-0000-0000-0000-000000009943');

DELETE FROM instructor_members
WHERE id = UUID_TO_BIN('00000000-0000-0000-0000-000000009943');

DELETE FROM members
WHERE id IN (
             UUID_TO_BIN('00000000-0000-0000-0000-000000009942'),
             UUID_TO_BIN('00000000-0000-0000-0000-000000009944')
    );

DELETE FROM course_categories
WHERE id = UUID_TO_BIN('00000000-0000-0000-0000-000000009941');

-- ---------------------------------------------------------------------------
-- Base reference data
-- ---------------------------------------------------------------------------
INSERT INTO course_categories (id, code, name, created_at, update_at)
VALUES (
        UUID_TO_BIN('00000000-0000-0000-0000-000000009941'),
        'PERF_WEBHOOK',
        'Performance Webhook',
        NOW(),
        NOW()
       );

INSERT INTO members (id, email, password, phone, role, status, name, has_onboarding, pf_image_url, withdrawal_at, created_at, update_at)
VALUES (
        UUID_TO_BIN('00000000-0000-0000-0000-000000009942'),
        'perf.webhook.instructor@example.com',
        '{bcrypt}$2a$10$NcGsvlexxeg3U1osgBbkjeBcao6WOc4j4MYyxd20dbaTAs0QqMFim',
        '010-9942-9942',
        'ROLE_INSTRUCTOR',
        'ACTIVE',
        'Perf Webhook Instructor',
        1,
        NULL,
        NULL,
        NOW(),
        NOW()
       );

INSERT INTO instructor_members (id, member_id, category_code, status, content, image_url, reject_reason, approved_at, responded_at, created_at, update_at)
VALUES (
        UUID_TO_BIN('00000000-0000-0000-0000-000000009943'),
        UUID_TO_BIN('00000000-0000-0000-0000-000000009942'),
        'PERF_WEBHOOK',
        'APPROVED',
        'Performance fixture instructor for Paid webhook scenario.',
        'https://example.com/perf-webhook-instructor.png',
        NULL,
        NOW(),
        NOW(),
        NOW(),
        NOW()
       );

INSERT INTO members (id, email, password, phone, role, status, name, has_onboarding, pf_image_url, withdrawal_at, created_at, update_at)
VALUES (
        UUID_TO_BIN('00000000-0000-0000-0000-000000009944'),
        'perf.payment.webhook@example.com',
        '{bcrypt}$2a$10$NcGsvlexxeg3U1osgBbkjeBcao6WOc4j4MYyxd20dbaTAs0QqMFim',
        '010-9944-9944',
        'ROLE_STUDENT',
        'ACTIVE',
        'Perf Payment Webhook Student',
        1,
        NULL,
        NULL,
        NOW(),
        NOW()
       );

INSERT INTO courses (
    id,
    course_category_id,
    member_instructor_id,
    title,
    description,
    address_main,
    address_detail,
    capacity,
    confirm_count,
    price,
    level,
    status,
    order_open_at,
    order_close_at,
    start_at,
    end_at,
    confirmed_at,
    reject_reason,
    deleted,
    deleted_at,
    created_at,
    update_at
)
WITH RECURSIVE seq AS (
    SELECT 0 AS n
    UNION ALL
    SELECT n + 1
    FROM seq
    WHERE n < 9
)
SELECT
    UUID_TO_BIN(CONCAT('00000000-0000-0000-0000-', LPAD(9950 + n, 12, '0'))),
    UUID_TO_BIN('00000000-0000-0000-0000-000000009941'),
    UUID_TO_BIN('00000000-0000-0000-0000-000000009943'),
    CONCAT('Payment Webhook Performance Course ', LPAD(n + 1, 2, '0')),
    CONCAT('Dedicated fixture course ', LPAD(n + 1, 2, '0'), ' for Paid webhook load testing.'),
    'Seoul',
    CONCAT('Webhook Lab ', LPAD(n + 1, 2, '0')),
    1000,
    0,
    50000,
    'BEGINNER',
    'OPEN',
    NOW() - INTERVAL 1 DAY,
    NOW() + INTERVAL 20 DAY,
    NOW() + INTERVAL 30 DAY,
    NOW() + INTERVAL 30 DAY + INTERVAL 2 HOUR,
    NOW() - INTERVAL 1 HOUR,
    NULL,
    0,
    NULL,
    NOW(),
    NOW()
FROM seq;

-- ---------------------------------------------------------------------------
-- Fresh pending orders and pending payments for Paid webhook scenario
-- 30 RPS * (5s warmup + 10s measure) = about 450 iterations
-- Seed 500 pending payments to keep a small spare buffer.
-- Spread fixtures across 10 courses so project-scale baseline does not collapse
-- into a single-course lock contention test.
-- Order UUID pattern:
--   92000000-0000-0000-0000-000000000001
-- Payment UUID pattern:
--   93000000-0000-0000-0000-000000000001
-- pg_key pattern:
--   pperfwebhook000001
-- ---------------------------------------------------------------------------
INSERT INTO orders (
    id,
    course_id,
    member_id,
    order_count,
    price_snap,
    total_price_snap,
    title_snap,
    status,
    cancelled_at,
    expire_at,
    version,
    created_at,
    update_at
)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1
    FROM seq
    WHERE n < 500
)
SELECT
    UUID_TO_BIN(CONCAT('92000000-0000-0000-0000-', LPAD(n, 12, '0'))),
    UUID_TO_BIN(CONCAT('00000000-0000-0000-0000-', LPAD(9950 + MOD(n - 1, 10), 12, '0'))),
    UUID_TO_BIN('00000000-0000-0000-0000-000000009944'),
    1,
    50000,
    50000,
    CONCAT('Payment Webhook Fixture Order ', LPAD(n, 4, '0')),
    'PENDING',
    NULL,
    NOW() + INTERVAL 1 DAY,
    0,
    NOW(),
    NOW()
FROM seq;

INSERT INTO payments (
    id,
    order_id,
    member_id,
    pg_key,
    total_price,
    paid_total_price,
    pay_way,
    status,
    paid_at,
    created_at,
    update_at
)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1
    FROM seq
    WHERE n < 500
)
SELECT
    UUID_TO_BIN(CONCAT('93000000-0000-0000-0000-', LPAD(n, 12, '0'))),
    UUID_TO_BIN(CONCAT('92000000-0000-0000-0000-', LPAD(n, 12, '0'))),
    UUID_TO_BIN('00000000-0000-0000-0000-000000009944'),
    CONCAT('pperfwebhook', LPAD(n, 6, '0')),
    50000,
    50000,
    'CARD',
    'PENDING',
    NULL,
    NOW(),
    NOW()
FROM seq;
