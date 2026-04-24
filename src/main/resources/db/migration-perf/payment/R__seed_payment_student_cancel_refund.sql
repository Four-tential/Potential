-- Performance fixture seed for student cancel/refund scenario
-- local + perf profile only
-- login password: testTEST123!@#

SET SESSION cte_max_recursion_depth = 1000;

-- ---------------------------------------------------------------------------
-- Cleanup previous performance fixtures
-- ---------------------------------------------------------------------------
DELETE r
FROM refunds r
         JOIN payments p ON r.payment_id = p.id
WHERE p.member_id = UUID_TO_BIN('00000000-0000-0000-0000-000000009964');

DELETE FROM payments
WHERE member_id = UUID_TO_BIN('00000000-0000-0000-0000-000000009964');

DELETE FROM orders
WHERE member_id = UUID_TO_BIN('00000000-0000-0000-0000-000000009964');

DELETE ci
FROM course_images ci
         JOIN courses c ON ci.course_id = c.id
WHERE c.member_instructor_id = UUID_TO_BIN('00000000-0000-0000-0000-000000009963');

DELETE FROM courses
WHERE member_instructor_id = UUID_TO_BIN('00000000-0000-0000-0000-000000009963');

DELETE FROM instructor_members
WHERE id = UUID_TO_BIN('00000000-0000-0000-0000-000000009963');

DELETE FROM members
WHERE id IN (
             UUID_TO_BIN('00000000-0000-0000-0000-000000009962'),
             UUID_TO_BIN('00000000-0000-0000-0000-000000009964')
    );

DELETE FROM course_categories
WHERE id = UUID_TO_BIN('00000000-0000-0000-0000-000000009961');

-- ---------------------------------------------------------------------------
-- Base reference data
-- ---------------------------------------------------------------------------
INSERT INTO course_categories (id, code, name, created_at, update_at)
VALUES (
        UUID_TO_BIN('00000000-0000-0000-0000-000000009961'),
        'PERF_REFUND',
        'Performance Refund',
        NOW(),
        NOW()
       );

INSERT INTO members (id, email, password, phone, role, status, name, has_onboarding, pf_image_url, withdrawal_at, created_at, update_at)
VALUES (
        UUID_TO_BIN('00000000-0000-0000-0000-000000009962'),
        'perf.refund.instructor@example.com',
        '{bcrypt}$2a$10$NcGsvlexxeg3U1osgBbkjeBcao6WOc4j4MYyxd20dbaTAs0QqMFim',
        '010-9962-9962',
        'ROLE_INSTRUCTOR',
        'ACTIVE',
        'Perf Refund Instructor',
        1,
        NULL,
        NULL,
        NOW(),
        NOW()
       );

INSERT INTO instructor_members (id, member_id, category_code, status, content, image_url, reject_reason, approved_at, responded_at, created_at, update_at)
VALUES (
        UUID_TO_BIN('00000000-0000-0000-0000-000000009963'),
        UUID_TO_BIN('00000000-0000-0000-0000-000000009962'),
        'PERF_REFUND',
        'APPROVED',
        'Performance fixture instructor for student cancel/refund scenario.',
        'https://example.com/perf-refund-instructor.png',
        NULL,
        NOW(),
        NOW(),
        NOW(),
        NOW()
       );

INSERT INTO members (id, email, password, phone, role, status, name, has_onboarding, pf_image_url, withdrawal_at, created_at, update_at)
VALUES (
        UUID_TO_BIN('00000000-0000-0000-0000-000000009964'),
        'perf.payment.refund@example.com',
        '{bcrypt}$2a$10$NcGsvlexxeg3U1osgBbkjeBcao6WOc4j4MYyxd20dbaTAs0QqMFim',
        '010-9964-9964',
        'ROLE_STUDENT',
        'ACTIVE',
        'Perf Payment Refund Student',
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
    UUID_TO_BIN(CONCAT('00000000-0000-0000-0000-', LPAD(9970 + n, 12, '0'))),
    UUID_TO_BIN('00000000-0000-0000-0000-000000009961'),
    UUID_TO_BIN('00000000-0000-0000-0000-000000009963'),
    CONCAT('Payment Refund Performance Course ', LPAD(n + 1, 2, '0')),
    CONCAT('Dedicated fixture course ', LPAD(n + 1, 2, '0'), ' for student cancel/refund load testing.'),
    'Seoul',
    CONCAT('Refund Lab ', LPAD(n + 1, 2, '0')),
    1000,
    50,
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
-- Paid orders and paid payments for student cancel/refund scenario
-- baseline target: 15 RPS * (5s warmup + 10s measure) = about 225 iterations
-- seed 500 paid payments to keep enough spare fixtures for repeated local runs
-- and future higher-RPS exploratory runs.
-- order UUID pattern:
--   94000000-0000-0000-0000-000000000001
-- payment UUID pattern:
--   95000000-0000-0000-0000-000000000001
-- pg_key pattern:
--   pperfrefund000001
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
    UUID_TO_BIN(CONCAT('94000000-0000-0000-0000-', LPAD(n, 12, '0'))),
    UUID_TO_BIN(CONCAT('00000000-0000-0000-0000-', LPAD(9970 + MOD(n - 1, 10), 12, '0'))),
    UUID_TO_BIN('00000000-0000-0000-0000-000000009964'),
    1,
    50000,
    50000,
    CONCAT('Payment Refund Fixture Order ', LPAD(n, 4, '0')),
    'PAID',
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
    UUID_TO_BIN(CONCAT('95000000-0000-0000-0000-', LPAD(n, 12, '0'))),
    UUID_TO_BIN(CONCAT('94000000-0000-0000-0000-', LPAD(n, 12, '0'))),
    UUID_TO_BIN('00000000-0000-0000-0000-000000009964'),
    CONCAT('pperfrefund', LPAD(n, 6, '0')),
    50000,
    50000,
    'CARD',
    'PAID',
    NOW() - INTERVAL 1 DAY,
    NOW(),
    NOW()
FROM seq;
