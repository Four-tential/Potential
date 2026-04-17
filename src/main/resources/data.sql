-- 모든 엔티티가 구성되면 flyway 를 이용할 것, 그 전까진 사용할 데이터 시딩

-- 코스 카테고리 데이터 시딩
INSERT IGNORE INTO course_categories (id, code, name, created_at, update_at)
VALUES
    (UUID_TO_BIN('00000000-0000-0000-0000-000000000101'), 'FITNESS',  '피트니스',  NOW(), NOW()),
    (UUID_TO_BIN('00000000-0000-0000-0000-000000000102'), 'YOGA',     '요가',      NOW(), NOW()),
    (UUID_TO_BIN('00000000-0000-0000-0000-000000000103'), 'PILATES',  '필라테스',  NOW(), NOW()),
    (UUID_TO_BIN('00000000-0000-0000-0000-000000000104'), 'DANCE',    '댄스',      NOW(), NOW()),
    (UUID_TO_BIN('00000000-0000-0000-0000-000000000105'), 'SWIMMING', '수영',      NOW(), NOW()),
    (UUID_TO_BIN('00000000-0000-0000-0000-000000000106'), 'RUNNING',  '러닝',      NOW(), NOW()),
    (UUID_TO_BIN('00000000-0000-0000-0000-000000000107'), 'CLIMBING', '클라이밍',  NOW(), NOW()),
    (UUID_TO_BIN('00000000-0000-0000-0000-000000000108'), 'BOXING',   '복싱',      NOW(), NOW());

-- Members
-- password: testTEST123!@#
-- 관리자
INSERT IGNORE INTO members (id, email, password, phone, role, status, name, has_onboarding, pf_image_url, withdrawal_at, created_at, update_at)
VALUES (
    UUID_TO_BIN('00000000-0000-0000-0000-000000000001'),
    'admin@admin.com',
    '{bcrypt}$2a$10$NcGsvlexxeg3U1osgBbkjeBcao6WOc4j4MYyxd20dbaTAs0QqMFim',
    '010-0000-0001',
    'ROLE_ADMIN',
    'ACTIVE',
    '관리자',
    false,
    NULL,
    NULL,
    NOW(),
    NOW()
);

-- 일반 학생 회원 1
INSERT IGNORE INTO members (id, email, password, phone, role, status, name, has_onboarding, pf_image_url, withdrawal_at, created_at, update_at)
VALUES (
    UUID_TO_BIN('00000000-0000-0000-0000-000000000002'),
    'user1@user.com',
    '{bcrypt}$2a$10$NcGsvlexxeg3U1osgBbkjeBcao6WOc4j4MYyxd20dbaTAs0QqMFim',
    '010-0000-0002',
    'ROLE_STUDENT',
    'ACTIVE',
    '홍길동',
    false,
    NULL,
    NULL,
    NOW(),
    NOW()
);

-- 일반 학생 회원 2
INSERT IGNORE INTO members (id, email, password, phone, role, status, name, has_onboarding, pf_image_url, withdrawal_at, created_at, update_at)
VALUES (
    UUID_TO_BIN('00000000-0000-0000-0000-000000000003'),
    'user2@user.com',
    '{bcrypt}$2a$10$NcGsvlexxeg3U1osgBbkjeBcao6WOc4j4MYyxd20dbaTAs0QqMFim',
    '010-0000-0003',
    'ROLE_STUDENT',
    'ACTIVE',
    '김철수',
    false,
    NULL,
    NULL,
    NOW(),
    NOW()
);
