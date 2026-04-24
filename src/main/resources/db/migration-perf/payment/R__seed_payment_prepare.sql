-- Performance fixture seed for payment prepare / payment read scenario
-- login password: testTEST123!@#

SET SESSION cte_max_recursion_depth = 1000;

-- ---------------------------------------------------------------------------
-- Cleanup previous performance fixtures
-- ---------------------------------------------------------------------------
DELETE r
FROM refunds r
         JOIN payments p ON r.payment_id = p.id
         JOIN orders o ON p.order_id = o.id
         JOIN members m ON o.member_id = m.id
WHERE m.email = 'perf.payment.prepare@example.com';

DELETE r
FROM refunds r
         JOIN payments p ON r.payment_id = p.id
         JOIN orders o ON p.order_id = o.id
WHERE o.member_id = UUID_TO_BIN('00000000-0000-0000-0000-000000009911');

DELETE p
FROM payments p
         JOIN orders o ON p.order_id = o.id
         JOIN members m ON o.member_id = m.id
WHERE m.email = 'perf.payment.prepare@example.com';

DELETE p
FROM payments p
         JOIN orders o ON p.order_id = o.id
WHERE o.member_id = UUID_TO_BIN('00000000-0000-0000-0000-000000009911');

DELETE o
FROM orders o
         JOIN members m ON o.member_id = m.id
WHERE m.email = 'perf.payment.prepare@example.com';

DELETE FROM orders
WHERE member_id = UUID_TO_BIN('00000000-0000-0000-0000-000000009911')
  AND course_id = UUID_TO_BIN('00000000-0000-0000-0000-000000009921');

DELETE ci
FROM course_images ci
         JOIN courses c ON ci.course_id = c.id
         JOIN instructor_members im ON c.member_instructor_id = im.id
         JOIN members m ON im.member_id = m.id
WHERE m.email = 'perf.instructor@example.com';

DELETE FROM course_images
WHERE course_id = UUID_TO_BIN('00000000-0000-0000-0000-000000009921');

DELETE c
FROM courses c
         JOIN instructor_members im ON c.member_instructor_id = im.id
         JOIN members m ON im.member_id = m.id
WHERE m.email = 'perf.instructor@example.com';

DELETE FROM courses
WHERE id = UUID_TO_BIN('00000000-0000-0000-0000-000000009921');

DELETE im
FROM instructor_members im
         JOIN members m ON im.member_id = m.id
WHERE m.email = 'perf.instructor@example.com';

DELETE FROM instructor_members
WHERE id = UUID_TO_BIN('00000000-0000-0000-0000-000000009903');

DELETE FROM members
WHERE email IN (
                'perf.instructor@example.com',
                'perf.payment.prepare@example.com'
    );

DELETE FROM members
WHERE id IN (
             UUID_TO_BIN('00000000-0000-0000-0000-000000009902'),
             UUID_TO_BIN('00000000-0000-0000-0000-000000009911')
    );

DELETE FROM course_categories
WHERE code = 'PERF_PAYMENT';

DELETE FROM course_categories
WHERE id = UUID_TO_BIN('00000000-0000-0000-0000-000000009901');

-- ---------------------------------------------------------------------------
-- Base reference data
-- ---------------------------------------------------------------------------
INSERT INTO course_categories (id, code, name, created_at, update_at)
VALUES (
        UUID_TO_BIN('00000000-0000-0000-0000-000000009901'),
        'PERF_PAYMENT',
        'Performance Payment',
        NOW(),
        NOW()
       );

INSERT INTO members (id, email, password, phone, role, status, name, has_onboarding, pf_image_url, withdrawal_at, created_at, update_at)
VALUES (
        UUID_TO_BIN('00000000-0000-0000-0000-000000009902'),
        'perf.instructor@example.com',
        '{bcrypt}$2a$10$NcGsvlexxeg3U1osgBbkjeBcao6WOc4j4MYyxd20dbaTAs0QqMFim',
        '010-9902-9902',
        'ROLE_INSTRUCTOR',
        'ACTIVE',
        'Perf Instructor',
        1,
        NULL,
        NULL,
        NOW(),
        NOW()
       );

INSERT INTO instructor_members (id, member_id, category_code, status, content, image_url, reject_reason, approved_at, responded_at, created_at, update_at)
VALUES (
        UUID_TO_BIN('00000000-0000-0000-0000-000000009903'),
        UUID_TO_BIN('00000000-0000-0000-0000-000000009902'),
        'PERF_PAYMENT',
        'APPROVED',
        'Performance fixture instructor for payment scenario.',
        'https://example.com/perf-instructor.png',
        NULL,
        NOW(),
        NOW(),
        NOW(),
        NOW()
       );

INSERT INTO members (id, email, password, phone, role, status, name, has_onboarding, pf_image_url, withdrawal_at, created_at, update_at)
VALUES (
        UUID_TO_BIN('00000000-0000-0000-0000-000000009911'),
        'perf.payment.prepare@example.com',
        '{bcrypt}$2a$10$NcGsvlexxeg3U1osgBbkjeBcao6WOc4j4MYyxd20dbaTAs0QqMFim',
        '010-9911-9911',
        'ROLE_STUDENT',
        'ACTIVE',
        'Perf Payment Student',
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
VALUES (
        UUID_TO_BIN('00000000-0000-0000-0000-000000009921'),
        UUID_TO_BIN('00000000-0000-0000-0000-000000009901'),
        UUID_TO_BIN('00000000-0000-0000-0000-000000009903'),
        'Payment Prepare Performance Course',
        'Dedicated fixture course for payment prepare and payment read load testing.',
        'Seoul',
        'Performance Lab',
        10000,
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
       );

-- ---------------------------------------------------------------------------
-- Fresh pending orders for payment prepare scenario
-- 30 RPS * (5s warmup + 10s measure) = 450 iterations
-- Seed 500 orders to keep a small spare buffer.
-- Order UUID pattern:
--   91000000-0000-0000-0000-000000000001
--   91000000-0000-0000-0000-000000000002
--   ...
--   91000000-0000-0000-0000-000000000500
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
    UUID_TO_BIN(CONCAT('91000000-0000-0000-0000-', LPAD(n, 12, '0'))),
    UUID_TO_BIN('00000000-0000-0000-0000-000000009921'),
    UUID_TO_BIN('00000000-0000-0000-0000-000000009911'),
    1,
    50000,
    50000,
    CONCAT('Payment Prepare Fixture Order ', LPAD(n, 4, '0')),
    'PENDING',
    NULL,
    NOW() + INTERVAL 1 DAY,
    0,
    NOW(),
    NOW()
FROM seq;
