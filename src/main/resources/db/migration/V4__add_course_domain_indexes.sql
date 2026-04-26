-- 코스 도메인 성능 개선 인덱스

-- 강사별 코스 조회
CREATE INDEX idx_courses_member_instructor_id
    ON courses (member_instructor_id);

-- 카테고리별 코스 필터링
CREATE INDEX idx_courses_course_category_id
    ON courses (course_category_id);

-- 상태별 코스 필터
CREATE INDEX idx_courses_status
    ON courses (status);

-- 코스별 이미지 조회
CREATE INDEX idx_course_images_course_id
    ON course_images (course_id);

-- 코스별 찜 조회/삭제
CREATE INDEX idx_course_wishlists_course_id
    ON course_wishlists (course_id);

-- 코스별 주문(수강생) 조회
CREATE INDEX idx_orders_course_id
    ON orders (course_id);

-- 코스별 출석 조회
CREATE INDEX idx_attendances_course_id
    ON attendances (course_id);

-- 코스 목록 필터링 복합 인덱스 (status 필터 + 카테고리 필터 동시 사용)
CREATE INDEX idx_courses_status_category
    ON courses (status, course_category_id);

-- 코스 목록 가격 정렬/범위 필터 (status 포함)
CREATE INDEX idx_courses_status_price
    ON courses (status, price);

-- 찜 삭제 멱등 처리 (member_id + course_id 복합)
CREATE INDEX idx_course_wishlists_member_course
    ON course_wishlists (member_id, course_id);
