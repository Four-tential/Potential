-- =========================================================================
-- 성능 테스트용 대량 시딩 (local 프로파일 전용)
--
-- 포함 규모 (일반 + 테스트 고정 계정):
--   members              2,012   (admin 1 + 기존 2 + 강사 100 + 학생 1,900 + 테스트 강사 2 + 테스트 학생 10)
--   instructor_members     102   (기존 1 + 강사 100 + 테스트 강사 2 - 기존은 user2)
--   courses              2,043   (기존 3 + 일반 2,000 + 테스트 강사 × 20 × 2)
--   course_images        6,124   (기존 4 + 코스당 3장)
--   orders              10,010   (일반 10,000 + 테스트 고정 10)
--   reviews             10,000
--   course_wishlists    19,103   (기존 3 + 일반 19,000 + 테스트 학생 × 10 × 10)
--   follows              9,551   (기존 1 + 일반 9,500 + 테스트 학생 × 5 × 10)
--
-- UUID prefix 규약:
--   01 : 일반 학생 member
--   02 : 일반 강사 member (role = ROLE_INSTRUCTOR)
--   03 : 일반 instructor_members 레코드
--   04 : 일반 courses
--   05 : course_images
--   06 : course_wishlists
--   07 : follows
--   08 : orders
--   09 : reviews
--   FE : 테스트 고정 학생 member
--   FD : 테스트 고정 강사 member
--   FC : 테스트 고정 instructor_members 레코드
--   FB : 테스트 고정 courses
--   FA : 테스트 고정 orders (수강생 명단 API 용)
--   F6 : 테스트 고정 course_wishlists
--   F7 : 테스트 고정 follows
--
-- 최초 실행에 수 분 소요. INSERT IGNORE 로 멱등 -- 재실행 시 no-op.
-- 프로덕션/DEV 환경에는 flyway.locations 에서 이 폴더를 제외하여 미적용.
-- =========================================================================

SET SESSION cte_max_recursion_depth = 100000;

-- -------------------------------------------------------------------------
-- [1/12] 일반 강사 회원 100명 (members)
-- -------------------------------------------------------------------------
INSERT IGNORE INTO members
    (id, email, password, phone, role, status, name, has_onboarding, pf_image_url, withdrawal_at, created_at, update_at)
WITH RECURSIVE seq(n) AS (
    SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 100
)
SELECT
    UUID_TO_BIN(CONCAT('00000000-0000-0000-0000-02', LPAD(HEX(n), 10, '0'))),
    CONCAT('instructor', n, '@example.com'),
    '{bcrypt}$2a$10$NcGsvlexxeg3U1osgBbkjeBcao6WOc4j4MYyxd20dbaTAs0QqMFim',
    CONCAT('010-2000-', LPAD(n, 4, '0')),
    'ROLE_INSTRUCTOR',
    'ACTIVE',
    CONCAT('강사', n),
    FALSE,
    NULL,
    NULL,
    NOW(),
    NOW()
FROM seq;

-- -------------------------------------------------------------------------
-- [2/12] 일반 instructor_members 100개 (모두 APPROVED)
-- -------------------------------------------------------------------------
INSERT IGNORE INTO instructor_members
    (id, member_id, category_code, status, content, image_url, reject_reason, approved_at, responded_at, created_at, update_at)
WITH RECURSIVE seq(n) AS (
    SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 100
)
SELECT
    UUID_TO_BIN(CONCAT('00000000-0000-0000-0000-03', LPAD(HEX(n), 10, '0'))),
    UUID_TO_BIN(CONCAT('00000000-0000-0000-0000-02', LPAD(HEX(n), 10, '0'))),
    ELT((n % 8) + 1, 'FITNESS', 'YOGA', 'PILATES', 'DANCE', 'SWIMMING', 'RUNNING', 'CLIMBING', 'BOXING'),
    'APPROVED',
    CONCAT('강사 ', n, ' 의 소개입니다. 성능 테스트용 시딩 데이터.'),
    'https://example.com/instructor/default.jpg',
    NULL,
    NOW(),
    NOW(),
    NOW(),
    NOW()
FROM seq;

-- -------------------------------------------------------------------------
-- [3/12] 일반 학생 회원 1,900명 (members)
-- -------------------------------------------------------------------------
INSERT IGNORE INTO members
    (id, email, password, phone, role, status, name, has_onboarding, pf_image_url, withdrawal_at, created_at, update_at)
WITH RECURSIVE seq(n) AS (
    SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 1900
)
SELECT
    UUID_TO_BIN(CONCAT('00000000-0000-0000-0000-01', LPAD(HEX(n), 10, '0'))),
    CONCAT('student', n, '@example.com'),
    '{bcrypt}$2a$10$NcGsvlexxeg3U1osgBbkjeBcao6WOc4j4MYyxd20dbaTAs0QqMFim',
    CONCAT('010-1000-', LPAD(n, 4, '0')),
    'ROLE_STUDENT',
    'ACTIVE',
    CONCAT('학생', n),
    FALSE,
    NULL,
    NULL,
    NOW(),
    NOW()
FROM seq;

-- -------------------------------------------------------------------------
-- [4/12] 테스트 고정 강사 회원 2명
-- -------------------------------------------------------------------------
INSERT IGNORE INTO members
    (id, email, password, phone, role, status, name, has_onboarding, pf_image_url, withdrawal_at, created_at, update_at)
VALUES
    (UUID_TO_BIN('00000000-0000-0000-0000-FD0000000001'), 'loadtest-instructor-1@example.com',
     '{bcrypt}$2a$10$NcGsvlexxeg3U1osgBbkjeBcao6WOc4j4MYyxd20dbaTAs0QqMFim',
     '010-9000-0001', 'ROLE_INSTRUCTOR', 'ACTIVE', '부하테스트강사1', FALSE, NULL, NULL, NOW(), NOW()),
    (UUID_TO_BIN('00000000-0000-0000-0000-FD0000000002'), 'loadtest-instructor-2@example.com',
     '{bcrypt}$2a$10$NcGsvlexxeg3U1osgBbkjeBcao6WOc4j4MYyxd20dbaTAs0QqMFim',
     '010-9000-0002', 'ROLE_INSTRUCTOR', 'ACTIVE', '부하테스트강사2', FALSE, NULL, NULL, NOW(), NOW());

-- -------------------------------------------------------------------------
-- [5/12] 테스트 고정 instructor_members 2개
-- -------------------------------------------------------------------------
INSERT IGNORE INTO instructor_members
    (id, member_id, category_code, status, content, image_url, reject_reason, approved_at, responded_at, created_at, update_at)
VALUES
    (UUID_TO_BIN('00000000-0000-0000-0000-FC0000000001'), UUID_TO_BIN('00000000-0000-0000-0000-FD0000000001'),
     'FITNESS', 'APPROVED', '부하테스트 강사 1', 'https://example.com/test1.jpg', NULL, NOW(), NOW(), NOW(), NOW()),
    (UUID_TO_BIN('00000000-0000-0000-0000-FC0000000002'), UUID_TO_BIN('00000000-0000-0000-0000-FD0000000002'),
     'YOGA', 'APPROVED', '부하테스트 강사 2', 'https://example.com/test2.jpg', NULL, NOW(), NOW(), NOW(), NOW());

-- -------------------------------------------------------------------------
-- [6/12] 테스트 고정 학생 10명
-- -------------------------------------------------------------------------
INSERT IGNORE INTO members
    (id, email, password, phone, role, status, name, has_onboarding, pf_image_url, withdrawal_at, created_at, update_at)
WITH RECURSIVE seq(n) AS (
    SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 10
)
SELECT
    UUID_TO_BIN(CONCAT('00000000-0000-0000-0000-FE', LPAD(HEX(n), 10, '0'))),
    CONCAT('loadtest-student-', n, '@example.com'),
    '{bcrypt}$2a$10$NcGsvlexxeg3U1osgBbkjeBcao6WOc4j4MYyxd20dbaTAs0QqMFim',
    CONCAT('010-9100-', LPAD(n, 4, '0')),
    'ROLE_STUDENT',
    'ACTIVE',
    CONCAT('부하테스트학생', n),
    FALSE,
    NULL,
    NULL,
    NOW(),
    NOW()
FROM seq;

-- -------------------------------------------------------------------------
-- [7/12] 일반 코스 2,000개 (OPEN 상태, 강사 100명에게 라운드로빈 배분)
-- -------------------------------------------------------------------------
INSERT IGNORE INTO courses
    (id, course_category_id, member_instructor_id, title, description,
     address_main, address_detail, capacity, confirm_count, price,
     level, status, order_open_at, order_close_at, start_at, end_at,
     confirmed_at, reject_reason, deleted, deleted_at, created_at, update_at)
WITH RECURSIVE seq(n) AS (
    SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 2000
)
SELECT
    UUID_TO_BIN(CONCAT('00000000-0000-0000-0000-04', LPAD(HEX(n), 10, '0'))),
    -- 카테고리 8개를 순환
    UUID_TO_BIN(CONCAT('00000000-0000-0000-0000-0000000001', LPAD(HEX(((n - 1) % 8) + 1), 2, '0'))),
    -- 100명 강사 라운드로빈
    UUID_TO_BIN(CONCAT('00000000-0000-0000-0000-03', LPAD(HEX(((n - 1) % 100) + 1), 10, '0'))),
    CONCAT('성능 테스트 코스 ', n),
    CONCAT('코스 ', n, ' 에 대한 설명입니다. 성능 테스트용 시딩 데이터.'),
    '서울특별시 강남구 테헤란로',
    CONCAT(n, '번 스튜디오'),
    10 + (n % 20),
    n % 5,
    20000 + (n % 100) * 1000,
    'BEGINNER',
    'OPEN',
    NOW() - INTERVAL (n % 20) DAY,
    NOW() + INTERVAL (10 + (n % 20)) DAY,
    NOW() + INTERVAL (15 + (n % 20)) DAY,
    NOW() + INTERVAL (15 + (n % 20)) DAY + INTERVAL 2 HOUR,
    NOW() - INTERVAL (n % 10) DAY,
    NULL,
    0,
    NULL,
    NOW(),
    NOW()
FROM seq;

-- -------------------------------------------------------------------------
-- [8/12] 테스트 고정 코스 40개 (테스트 강사 1·2 각 20개)
--        테스트 강사 1의 첫 코스(FB...00000001)는 수강생 명단 API 측정 대상
-- -------------------------------------------------------------------------
INSERT IGNORE INTO courses
    (id, course_category_id, member_instructor_id, title, description,
     address_main, address_detail, capacity, confirm_count, price,
     level, status, order_open_at, order_close_at, start_at, end_at,
     confirmed_at, reject_reason, deleted, deleted_at, created_at, update_at)
WITH RECURSIVE seq(n) AS (
    SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 40
)
SELECT
    UUID_TO_BIN(CONCAT('00000000-0000-0000-0000-FB', LPAD(HEX(n), 10, '0'))),
    UUID_TO_BIN('00000000-0000-0000-0000-000000000101'), -- 모두 FITNESS
    -- 앞 20개는 테스트 강사 1, 뒤 20개는 테스트 강사 2
    CASE WHEN n <= 20
         THEN UUID_TO_BIN('00000000-0000-0000-0000-FC0000000001')
         ELSE UUID_TO_BIN('00000000-0000-0000-0000-FC0000000002')
    END,
    CONCAT('테스트 코스 ', n),
    CONCAT('테스트 코스 ', n, ' 설명'),
    '서울특별시 강남구',
    CONCAT(n, '호실'),
    30,
    10,
    50000,
    'BEGINNER',
    'OPEN',
    NOW() - INTERVAL 5 DAY,
    NOW() + INTERVAL 10 DAY,
    NOW() + INTERVAL 15 DAY,
    NOW() + INTERVAL 15 DAY + INTERVAL 2 HOUR,
    NOW() - INTERVAL 3 DAY,
    NULL,
    0,
    NULL,
    NOW(),
    NOW()
FROM seq;

-- -------------------------------------------------------------------------
-- [9/12] 코스 이미지 6,120개 (일반 2,000 × 3 + 테스트 40 × 3)
-- -------------------------------------------------------------------------
INSERT IGNORE INTO course_images
    (id, course_id, image_url, created_at, update_at)
WITH RECURSIVE
    course_seq(cn) AS (
        SELECT 1 UNION ALL SELECT cn + 1 FROM course_seq WHERE cn < 2000
    ),
    img_seq(kn) AS (
        SELECT 1 UNION ALL SELECT kn + 1 FROM img_seq WHERE kn < 3
    )
SELECT
    UUID_TO_BIN(CONCAT('00000000-0000-0000-0000-05', LPAD(HEX((cn - 1) * 3 + kn), 10, '0'))),
    UUID_TO_BIN(CONCAT('00000000-0000-0000-0000-04', LPAD(HEX(cn), 10, '0'))),
    CONCAT('https://images.example.com/course/', cn, '/', kn, '.jpg'),
    NOW(),
    NOW()
FROM course_seq, img_seq;

-- 테스트 코스 이미지 (40 × 3 = 120)
INSERT IGNORE INTO course_images
    (id, course_id, image_url, created_at, update_at)
WITH RECURSIVE
    course_seq(cn) AS (
        SELECT 1 UNION ALL SELECT cn + 1 FROM course_seq WHERE cn < 40
    ),
    img_seq(kn) AS (
        SELECT 1 UNION ALL SELECT kn + 1 FROM img_seq WHERE kn < 3
    )
SELECT
    UUID_TO_BIN(CONCAT('00000000-0000-0000-0000-F5', LPAD(HEX((cn - 1) * 3 + kn), 10, '0'))),
    UUID_TO_BIN(CONCAT('00000000-0000-0000-0000-FB', LPAD(HEX(cn), 10, '0'))),
    CONCAT('https://images.example.com/test-course/', cn, '/', kn, '.jpg'),
    NOW(),
    NOW()
FROM course_seq, img_seq;

-- -------------------------------------------------------------------------
-- [10/12] 일반 주문 10,000개 (CONFIRMED 상태)
-- -------------------------------------------------------------------------
INSERT IGNORE INTO orders
    (id, course_id, member_id, order_count, price_snap, total_price_snap, title_snap,
     status, cancelled_at, expire_at, version, created_at, update_at)
WITH RECURSIVE seq(n) AS (
    SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 10000
)
SELECT
    UUID_TO_BIN(CONCAT('00000000-0000-0000-0000-08', LPAD(HEX(n), 10, '0'))),
    -- 2,000 코스를 순환
    UUID_TO_BIN(CONCAT('00000000-0000-0000-0000-04', LPAD(HEX(((n - 1) % 2000) + 1), 10, '0'))),
    -- 1,900 학생을 순환
    UUID_TO_BIN(CONCAT('00000000-0000-0000-0000-01', LPAD(HEX(((n - 1) % 1900) + 1), 10, '0'))),
    1,
    50000,
    50000,
    CONCAT('성능 테스트 코스 ', ((n - 1) % 2000) + 1),
    'CONFIRMED',
    NULL,
    NOW() + INTERVAL 30 DAY,
    0,
    NOW() - INTERVAL (n % 30) DAY,
    NOW() - INTERVAL (n % 30) DAY
FROM seq;

-- 테스트 주문 10개 (테스트 강사 1 의 첫 코스에 테스트 학생 10명이 모두 CONFIRMED)
INSERT IGNORE INTO orders
    (id, course_id, member_id, order_count, price_snap, total_price_snap, title_snap,
     status, cancelled_at, expire_at, version, created_at, update_at)
WITH RECURSIVE seq(n) AS (
    SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 10
)
SELECT
    UUID_TO_BIN(CONCAT('00000000-0000-0000-0000-FA', LPAD(HEX(n), 10, '0'))),
    UUID_TO_BIN('00000000-0000-0000-0000-FB0000000001'),
    UUID_TO_BIN(CONCAT('00000000-0000-0000-0000-FE', LPAD(HEX(n), 10, '0'))),
    1,
    50000,
    50000,
    '테스트 코스 1',
    'CONFIRMED',
    NULL,
    NOW() + INTERVAL 30 DAY,
    0,
    NOW(),
    NOW()
FROM seq;

-- -------------------------------------------------------------------------
-- [11/12] 리뷰 10,000개 (일반 주문 1:1 매핑, rating 1-5 분포)
-- -------------------------------------------------------------------------
INSERT IGNORE INTO reviews
    (id, member_id, course_id, order_id, rating, content, created_at, update_at)
WITH RECURSIVE seq(n) AS (
    SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 10000
)
SELECT
    UUID_TO_BIN(CONCAT('00000000-0000-0000-0000-09', LPAD(HEX(n), 10, '0'))),
    UUID_TO_BIN(CONCAT('00000000-0000-0000-0000-01', LPAD(HEX(((n - 1) % 1900) + 1), 10, '0'))),
    UUID_TO_BIN(CONCAT('00000000-0000-0000-0000-04', LPAD(HEX(((n - 1) % 2000) + 1), 10, '0'))),
    UUID_TO_BIN(CONCAT('00000000-0000-0000-0000-08', LPAD(HEX(n), 10, '0'))),
    ((n - 1) % 5) + 1,
    CONCAT('리뷰 ', n, ' 내용입니다.'),
    NOW() - INTERVAL (n % 60) DAY,
    NOW() - INTERVAL (n % 60) DAY
FROM seq;

-- -------------------------------------------------------------------------
-- [12/12] 찜 19,100개 + 팔로우 9,550개
-- -------------------------------------------------------------------------
-- 일반 찜: 학생 1,900 × 10 = 19,000 (각 학생이 연속 10개 코스를 찜)
INSERT IGNORE INTO course_wishlists
    (id, member_id, course_id, created_at, update_at)
WITH RECURSIVE
    student_seq(sn) AS (
        SELECT 1 UNION ALL SELECT sn + 1 FROM student_seq WHERE sn < 1900
    ),
    wish_seq(kn) AS (
        SELECT 0 UNION ALL SELECT kn + 1 FROM wish_seq WHERE kn < 9
    )
SELECT
    UUID_TO_BIN(CONCAT('00000000-0000-0000-0000-06', LPAD(HEX((sn - 1) * 10 + kn + 1), 10, '0'))),
    UUID_TO_BIN(CONCAT('00000000-0000-0000-0000-01', LPAD(HEX(sn), 10, '0'))),
    UUID_TO_BIN(CONCAT('00000000-0000-0000-0000-04', LPAD(HEX(((sn + kn - 1) % 2000) + 1), 10, '0'))),
    NOW() - INTERVAL (kn) DAY,
    NOW() - INTERVAL (kn) DAY
FROM student_seq, wish_seq;

-- 테스트 학생의 찜: 10명 × 10 = 100 (각자 연속 10개 regular 코스 찜)
INSERT IGNORE INTO course_wishlists
    (id, member_id, course_id, created_at, update_at)
WITH RECURSIVE
    student_seq(sn) AS (
        SELECT 1 UNION ALL SELECT sn + 1 FROM student_seq WHERE sn < 10
    ),
    wish_seq(kn) AS (
        SELECT 0 UNION ALL SELECT kn + 1 FROM wish_seq WHERE kn < 9
    )
SELECT
    UUID_TO_BIN(CONCAT('00000000-0000-0000-0000-F6', LPAD(HEX((sn - 1) * 10 + kn + 1), 10, '0'))),
    UUID_TO_BIN(CONCAT('00000000-0000-0000-0000-FE', LPAD(HEX(sn), 10, '0'))),
    UUID_TO_BIN(CONCAT('00000000-0000-0000-0000-04', LPAD(HEX(((sn - 1) * 10 + kn) % 2000 + 1), 10, '0'))),
    NOW() - INTERVAL (kn) DAY,
    NOW() - INTERVAL (kn) DAY
FROM student_seq, wish_seq;

-- 일반 팔로우: 학생 1,900 × 5 instructor_members = 9,500 (연속 5명)
INSERT IGNORE INTO follows
    (id, member_id, member_instructor_id, created_at, update_at)
WITH RECURSIVE
    student_seq(sn) AS (
        SELECT 1 UNION ALL SELECT sn + 1 FROM student_seq WHERE sn < 1900
    ),
    k_seq(kn) AS (
        SELECT 0 UNION ALL SELECT kn + 1 FROM k_seq WHERE kn < 4
    )
SELECT
    UUID_TO_BIN(CONCAT('00000000-0000-0000-0000-07', LPAD(HEX((sn - 1) * 5 + kn + 1), 10, '0'))),
    UUID_TO_BIN(CONCAT('00000000-0000-0000-0000-01', LPAD(HEX(sn), 10, '0'))),
    UUID_TO_BIN(CONCAT('00000000-0000-0000-0000-03', LPAD(HEX(((sn + kn - 1) % 100) + 1), 10, '0'))),
    NOW() - INTERVAL (kn) DAY,
    NOW() - INTERVAL (kn) DAY
FROM student_seq, k_seq;

-- 테스트 학생의 팔로우: 10 × 5 = 50 (연속 5명의 regular 강사)
INSERT IGNORE INTO follows
    (id, member_id, member_instructor_id, created_at, update_at)
WITH RECURSIVE
    student_seq(sn) AS (
        SELECT 1 UNION ALL SELECT sn + 1 FROM student_seq WHERE sn < 10
    ),
    k_seq(kn) AS (
        SELECT 0 UNION ALL SELECT kn + 1 FROM k_seq WHERE kn < 4
    )
SELECT
    UUID_TO_BIN(CONCAT('00000000-0000-0000-0000-F7', LPAD(HEX((sn - 1) * 5 + kn + 1), 10, '0'))),
    UUID_TO_BIN(CONCAT('00000000-0000-0000-0000-FE', LPAD(HEX(sn), 10, '0'))),
    UUID_TO_BIN(CONCAT('00000000-0000-0000-0000-03', LPAD(HEX(((sn + kn - 1) % 100) + 1), 10, '0'))),
    NOW() - INTERVAL (kn) DAY,
    NOW() - INTERVAL (kn) DAY
FROM student_seq, k_seq;
