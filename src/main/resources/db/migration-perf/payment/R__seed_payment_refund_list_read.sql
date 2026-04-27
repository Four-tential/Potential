-- Performance fixture seed for refund list read scenario
-- local + perf profile only
-- login password: testTEST123!@#

SET SESSION cte_max_recursion_depth = 2000;

-- ---------------------------------------------------------------------------
-- Cleanup previous refund-list read fixtures
-- ---------------------------------------------------------------------------
DELETE r
FROM refunds r
         JOIN payments p ON r.payment_id = p.id
         JOIN members m ON p.member_id = m.id
WHERE m.email = 'perf.payment.refund.list@example.com';

DELETE r
FROM refunds r
         JOIN payments p ON r.payment_id = p.id
WHERE p.member_id = UUID_TO_BIN('00000000-0000-0000-0000-000000009984');

DELETE p
FROM payments p
         JOIN members m ON p.member_id = m.id
WHERE m.email = 'perf.payment.refund.list@example.com';

DELETE FROM payments
WHERE member_id = UUID_TO_BIN('00000000-0000-0000-0000-000000009984');

DELETE o
FROM orders o
         JOIN members m ON o.member_id = m.id
WHERE m.email = 'perf.payment.refund.list@example.com';

DELETE FROM orders
WHERE member_id = UUID_TO_BIN('00000000-0000-0000-0000-000000009984');

DELETE ci
FROM course_images ci
         JOIN courses c ON ci.course_id = c.id
         JOIN instructor_members im ON c.member_instructor_id = im.id
         JOIN members m ON im.member_id = m.id
WHERE m.email = 'perf.refund.list.instructor@example.com';

DELETE ci
FROM course_images ci
         JOIN courses c ON ci.course_id = c.id
WHERE c.member_instructor_id = UUID_TO_BIN('00000000-0000-0000-0000-000000009983');

DELETE c
FROM courses c
         JOIN instructor_members im ON c.member_instructor_id = im.id
         JOIN members m ON im.member_id = m.id
WHERE m.email = 'perf.refund.list.instructor@example.com';

DELETE FROM courses
WHERE member_instructor_id = UUID_TO_BIN('00000000-0000-0000-0000-000000009983');

DELETE im
FROM instructor_members im
         JOIN members m ON im.member_id = m.id
WHERE m.email = 'perf.refund.list.instructor@example.com';

DELETE FROM instructor_members
WHERE id = UUID_TO_BIN('00000000-0000-0000-0000-000000009983');

DELETE FROM members
WHERE email IN (
                'perf.refund.list.instructor@example.com',
                'perf.payment.refund.list@example.com'
    );

DELETE FROM members
WHERE id IN (
             UUID_TO_BIN('00000000-0000-0000-0000-000000009982'),
             UUID_TO_BIN('00000000-0000-0000-0000-000000009984')
    );

DELETE FROM course_categories
WHERE code = 'PERF_REFUND_LIST';

DELETE FROM course_categories
WHERE id = UUID_TO_BIN('00000000-0000-0000-0000-000000009981');

-- ---------------------------------------------------------------------------
-- Base reference data
-- ---------------------------------------------------------------------------
INSERT INTO course_categories (id, code, name, created_at, update_at)
VALUES (
        UUID_TO_BIN('00000000-0000-0000-0000-000000009981'),
        'PERF_REFUND_LIST',
        'Performance Refund List',
        NOW(),
        NOW()
       );

INSERT INTO members (id, email, password, phone, role, status, name, has_onboarding, pf_image_url, withdrawal_at, created_at, update_at)
VALUES (
        UUID_TO_BIN('00000000-0000-0000-0000-000000009982'),
        'perf.refund.list.instructor@example.com',
        '{bcrypt}$2a$10$NcGsvlexxeg3U1osgBbkjeBcao6WOc4j4MYyxd20dbaTAs0QqMFim',
        '010-9982-9982',
        'ROLE_INSTRUCTOR',
        'ACTIVE',
        'Perf Refund List Instructor',
        1,
        NULL,
        NULL,
        NOW(),
        NOW()
       );

INSERT INTO instructor_members (id, member_id, category_code, status, content, image_url, reject_reason, approved_at, responded_at, created_at, update_at)
VALUES (
        UUID_TO_BIN('00000000-0000-0000-0000-000000009983'),
        UUID_TO_BIN('00000000-0000-0000-0000-000000009982'),
        'PERF_REFUND_LIST',
        'APPROVED',
        'Performance fixture instructor for refund list read scenario.',
        'https://example.com/perf-refund-list-instructor.png',
        NULL,
        NOW(),
        NOW(),
        NOW(),
        NOW()
       );

INSERT INTO members (id, email, password, phone, role, status, name, has_onboarding, pf_image_url, withdrawal_at, created_at, update_at)
VALUES (
        UUID_TO_BIN('00000000-0000-0000-0000-000000009984'),
        'perf.payment.refund.list@example.com',
        '{bcrypt}$2a$10$NcGsvlexxeg3U1osgBbkjeBcao6WOc4j4MYyxd20dbaTAs0QqMFim',
        '010-9984-9984',
        'ROLE_STUDENT',
        'ACTIVE',
        'Perf Payment Refund List Student',
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
    UUID_TO_BIN(CONCAT('00000000-0000-0000-0000-', LPAD(99850 + n, 12, '0'))),
    UUID_TO_BIN('00000000-0000-0000-0000-000000009981'),
    UUID_TO_BIN('00000000-0000-0000-0000-000000009983'),
    CONCAT('Payment Refund List Performance Course ', LPAD(n + 1, 2, '0')),
    CONCAT('Dedicated fixture course ', LPAD(n + 1, 2, '0'), ' for refund list read load testing.'),
    'Seoul',
    CONCAT('Refund List Lab ', LPAD(n + 1, 2, '0')),
    2000,
    0,
    50000,
    'BEGINNER',
    'OPEN',
    NOW() - INTERVAL 30 DAY,
    NOW() + INTERVAL 30 DAY,
    NOW() + INTERVAL 60 DAY,
    NOW() + INTERVAL 60 DAY + INTERVAL 2 HOUR,
    NOW() - INTERVAL 20 DAY,
    NULL,
    0,
    NULL,
    NOW(),
    NOW()
FROM seq;

-- ---------------------------------------------------------------------------
-- Refunded orders, refunded payments, completed refunds for refund list read
-- 1000 row fixture dedicated to payment/refund read optimization comparison
-- order UUID pattern:
--   97000000-0000-0000-0000-000000000001
-- payment UUID pattern:
--   98000000-0000-0000-0000-000000000001
-- refund UUID pattern:
--   99000000-0000-0000-0000-000000000001
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
    WHERE n < 1000
)
SELECT
    UUID_TO_BIN(CONCAT('97000000-0000-0000-0000-', LPAD(n, 12, '0'))),
    UUID_TO_BIN(CONCAT('00000000-0000-0000-0000-', LPAD(99850 + MOD(n - 1, 10), 12, '0'))),
    UUID_TO_BIN('00000000-0000-0000-0000-000000009984'),
    0,
    50000,
    50000,
    CONCAT('Payment Refund List Fixture Order ', LPAD(n, 4, '0')),
    'CANCELLED',
    NOW() - INTERVAL 5 DAY,
    NOW() + INTERVAL 1 DAY,
    0,
    NOW() - INTERVAL 15 DAY,
    NOW() - INTERVAL 5 DAY
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
    WHERE n < 1000
)
SELECT
    UUID_TO_BIN(CONCAT('98000000-0000-0000-0000-', LPAD(n, 12, '0'))),
    UUID_TO_BIN(CONCAT('97000000-0000-0000-0000-', LPAD(n, 12, '0'))),
    UUID_TO_BIN('00000000-0000-0000-0000-000000009984'),
    CONCAT('pperfrefundlist', LPAD(n, 6, '0')),
    50000,
    50000,
    'CARD',
    'REFUNDED',
    NOW() - INTERVAL 10 DAY,
    NOW() - INTERVAL 15 DAY,
    NOW() - INTERVAL 5 DAY
FROM seq;

INSERT INTO refunds (
    id,
    payment_id,
    refund_price,
    cancel_count,
    reason,
    status,
    refunded_at,
    created_at,
    update_at
)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1
    FROM seq
    WHERE n < 1000
)
SELECT
    UUID_TO_BIN(CONCAT('99000000-0000-0000-0000-', LPAD(n, 12, '0'))),
    UUID_TO_BIN(CONCAT('98000000-0000-0000-0000-', LPAD(n, 12, '0'))),
    50000,
    1,
    'CANCEL',
    'COMPLETED',
    NOW() - INTERVAL 5 DAY + INTERVAL MOD(n - 1, 60) MINUTE,
    NOW() - INTERVAL 5 DAY + INTERVAL MOD(n - 1, 60) MINUTE,
    NOW() - INTERVAL 5 DAY + INTERVAL MOD(n - 1, 60) MINUTE
FROM seq;
