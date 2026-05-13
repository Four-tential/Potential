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

-- password: testTEST123!@#
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

INSERT IGNORE INTO members (id, email, password, phone, role, status, name, has_onboarding, pf_image_url, withdrawal_at, created_at, update_at)
VALUES (
    UUID_TO_BIN('00000000-0000-0000-0000-000000000003'),
    'user2@user.com',
    '{bcrypt}$2a$10$NcGsvlexxeg3U1osgBbkjeBcao6WOc4j4MYyxd20dbaTAs0QqMFim',
    '010-0000-0003',
    'ROLE_INSTRUCTOR',
    'ACTIVE',
    '김철수',
    false,
    NULL,
    NULL,
    NOW(),
    NOW()
);

INSERT IGNORE INTO instructor_members (id, member_id, category_code, status, content, image_url, reject_reason, approved_at, responded_at, created_at, update_at)
VALUES (
    UUID_TO_BIN('00000000-0000-0000-0000-000000000011'),
    UUID_TO_BIN('00000000-0000-0000-0000-000000000003'),
    'FITNESS',
    'APPROVED',
    '10년 경력의 피트니스 강사입니다.',
    'https://example.com/instructor/profile.jpg',
    NULL,
    NOW(),
    NOW(),
    NOW(),
    NOW()
);

INSERT IGNORE INTO follows (id, member_id, member_instructor_id, created_at, update_at)
VALUES (
    UUID_TO_BIN('00000000-0000-0000-0000-000000000021'),
    UUID_TO_BIN('00000000-0000-0000-0000-000000000002'),
    UUID_TO_BIN('00000000-0000-0000-0000-000000000011'),
    NOW(),
    NOW()
);

INSERT IGNORE INTO courses (
    id, course_category_id, member_instructor_id,
    title, description, address_main, address_detail,
    capacity, confirm_count, price, level, status,
    order_open_at, order_close_at, start_at, end_at,
    confirmed_at, reject_reason, deleted, deleted_at, created_at, update_at
)
VALUES
    (
        UUID_TO_BIN('00000000-0000-0000-0000-000000000201'),
        UUID_TO_BIN('00000000-0000-0000-0000-000000000101'),
        UUID_TO_BIN('00000000-0000-0000-0000-000000000011'),
        '퇴근 후 피트니스 입문',
        '초보자를 위한 전신 근력 운동 클래스입니다.',
        '서울특별시 강남구 테헤란로',
        '피트니스 스튜디오 3층',
        20, 3, 50000, 'BEGINNER', 'OPEN',
        NOW() - INTERVAL 10 DAY,
        NOW() + INTERVAL 5 DAY,
        NOW() + INTERVAL 8 DAY,
        NOW() + INTERVAL 8 DAY + INTERVAL 2 HOUR,
        NOW(), NULL, 0, NULL,
        NOW() - INTERVAL 3 DAY,
        NOW() - INTERVAL 3 DAY
    ),
    (
        UUID_TO_BIN('00000000-0000-0000-0000-000000000202'),
        UUID_TO_BIN('00000000-0000-0000-0000-000000000102'),
        UUID_TO_BIN('00000000-0000-0000-0000-000000000011'),
        '아침 요가 루틴',
        '하루를 가볍게 시작하는 스트레칭 중심 요가 클래스입니다.',
        '서울특별시 마포구 월드컵북로',
        '요가룸 A',
        15, 5, 42000, 'STARTER', 'OPEN',
        NOW() - INTERVAL 9 DAY,
        NOW() + INTERVAL 6 DAY,
        NOW() + INTERVAL 9 DAY,
        NOW() + INTERVAL 9 DAY + INTERVAL 2 HOUR,
        NOW(), NULL, 0, NULL,
        NOW() - INTERVAL 2 DAY,
        NOW() - INTERVAL 2 DAY
    ),
    (
        UUID_TO_BIN('00000000-0000-0000-0000-000000000203'),
        UUID_TO_BIN('00000000-0000-0000-0000-000000000104'),
        UUID_TO_BIN('00000000-0000-0000-0000-000000000011'),
        'K-POP 댄스 원데이',
        '인기 안무를 배우는 원데이 댄스 클래스입니다.',
        '서울특별시 성동구 왕십리로',
        '댄스 스튜디오 B1',
        25, 8, 35000, 'BEGINNER', 'PREPARATION',
        NOW() - INTERVAL 8 DAY,
        NOW() + INTERVAL 7 DAY,
        NOW() + INTERVAL 10 DAY,
        NOW() + INTERVAL 10 DAY + INTERVAL 2 HOUR,
        NULL, NULL, 0, NULL,
        NOW() - INTERVAL 1 DAY,
        NOW() - INTERVAL 1 DAY
    );

INSERT IGNORE INTO course_images (id, course_id, image_url, created_at, update_at)
VALUES
    (
        UUID_TO_BIN('00000000-0000-0000-0000-000000000301'),
        UUID_TO_BIN('00000000-0000-0000-0000-000000000201'),
        'https://images.unsplash.com/photo-1517836357463-d25dfeac3438',
        NOW(), NOW()
    ),
    (
        UUID_TO_BIN('00000000-0000-0000-0000-000000000304'),
        UUID_TO_BIN('00000000-0000-0000-0000-000000000201'),
        'https://images.unsplash.com/photo-1571019613454-1cb2f99b2d8b',
        NOW(), NOW()
    ),
    (
        UUID_TO_BIN('00000000-0000-0000-0000-000000000302'),
        UUID_TO_BIN('00000000-0000-0000-0000-000000000202'),
        'https://images.unsplash.com/photo-1544367567-0f2fcb009e0b',
        NOW(), NOW()
    ),
    (
        UUID_TO_BIN('00000000-0000-0000-0000-000000000303'),
        UUID_TO_BIN('00000000-0000-0000-0000-000000000203'),
        'https://images.unsplash.com/photo-1504609813442-a8924e83f76e',
        NOW(), NOW()
    );

INSERT IGNORE INTO course_wishlists (id, member_id, course_id, created_at, update_at)
VALUES
    (
        UUID_TO_BIN('00000000-0000-0000-0000-000000000401'),
        UUID_TO_BIN('00000000-0000-0000-0000-000000000002'),
        UUID_TO_BIN('00000000-0000-0000-0000-000000000201'),
        NOW() - INTERVAL 3 DAY,
        NOW() - INTERVAL 3 DAY
    ),
    (
        UUID_TO_BIN('00000000-0000-0000-0000-000000000402'),
        UUID_TO_BIN('00000000-0000-0000-0000-000000000002'),
        UUID_TO_BIN('00000000-0000-0000-0000-000000000202'),
        NOW() - INTERVAL 2 DAY,
        NOW() - INTERVAL 2 DAY
    ),
    (
        UUID_TO_BIN('00000000-0000-0000-0000-000000000403'),
        UUID_TO_BIN('00000000-0000-0000-0000-000000000002'),
        UUID_TO_BIN('00000000-0000-0000-0000-000000000203'),
        NOW() - INTERVAL 1 DAY,
        NOW() - INTERVAL 1 DAY
    );