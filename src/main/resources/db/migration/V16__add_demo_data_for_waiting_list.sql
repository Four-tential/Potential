-- [0] 사용하지 않는 이전 RDB 기반 대기열 테이블 삭제
DROP TABLE IF EXISTS waiting_list;

-- [1] 시연용 코스 생성 (정원 1명)
INSERT INTO courses (
    id, course_category_id, member_instructor_id,
    title, description, address_main, address_detail,
    capacity, confirm_count, price, level, status,
    order_open_at, order_close_at, start_at, end_at,
    confirmed_at, reject_reason, deleted, deleted_at, created_at, update_at
)
VALUES (
    UUID_TO_BIN('00000000-0000-0000-0000-000000000D01'),
    UUID_TO_BIN('00000000-0000-0000-0000-000000000101'), -- FITNESS
    UUID_TO_BIN('00000000-0000-0000-0000-000000000011'), -- 김철수 강사
    '[시연용] 실시간 대기열 테스트 코스',
    '대기열 시스템 시연을 위한 정원 1명의 테스트 코스입니다.',
    '서울특별시 강남구 테헤란로',
    '데모 스튜디오 1호',
    1, 0, 10000, 'BEGINNER', 'OPEN',
    NOW() - INTERVAL 1 DAY,
    NOW() + INTERVAL 30 DAY,
    NOW() + INTERVAL 31 DAY,
    NOW() + INTERVAL 31 DAY + INTERVAL 2 HOUR,
    NOW(), NULL, 0, NULL,
    NOW(), NOW()
);

-- [2] 시연용 학생 계정 추가 (demo-student-1 ~ 5)
-- password: testTEST123!@#
INSERT IGNORE INTO members (id, email, password, phone, role, status, name, has_onboarding, pf_image_url, withdrawal_at, created_at, update_at)
VALUES
    (UUID_TO_BIN('00000000-0000-0000-0000-000000000D11'), 'demo-student-1@example.com', '{bcrypt}$2a$10$NcGsvlexxeg3U1osgBbkjeBcao6WOc4j4MYyxd20dbaTAs0QqMFim', '010-9999-0001', 'ROLE_STUDENT', 'ACTIVE', '데모학생1', false, NULL, NULL, NOW(), NOW()),
    (UUID_TO_BIN('00000000-0000-0000-0000-000000000D12'), 'demo-student-2@example.com', '{bcrypt}$2a$10$NcGsvlexxeg3U1osgBbkjeBcao6WOc4j4MYyxd20dbaTAs0QqMFim', '010-9999-0002', 'ROLE_STUDENT', 'ACTIVE', '데모학생2', false, NULL, NULL, NOW(), NOW()),
    (UUID_TO_BIN('00000000-0000-0000-0000-000000000D13'), 'demo-student-3@example.com', '{bcrypt}$2a$10$NcGsvlexxeg3U1osgBbkjeBcao6WOc4j4MYyxd20dbaTAs0QqMFim', '010-9999-0003', 'ROLE_STUDENT', 'ACTIVE', '데모학생3', false, NULL, NULL, NOW(), NOW()),
    (UUID_TO_BIN('00000000-0000-0000-0000-000000000D14'), 'demo-student-4@example.com', '{bcrypt}$2a$10$NcGsvlexxeg3U1osgBbkjeBcao6WOc4j4MYyxd20dbaTAs0QqMFim', '010-9999-0004', 'ROLE_STUDENT', 'ACTIVE', '데모학생4', false, NULL, NULL, NOW(), NOW()),
    (UUID_TO_BIN('00000000-0000-0000-0000-000000000D15'), 'demo-student-5@example.com', '{bcrypt}$2a$10$NcGsvlexxeg3U1osgBbkjeBcao6WOc4j4MYyxd20dbaTAs0QqMFim', '010-9999-0005', 'ROLE_STUDENT', 'ACTIVE', '데모학생5', false, NULL, NULL, NOW(), NOW());
