-- 회원 도메인 성능 개선 인덱스

-- 강사별 팔로워 조회
CREATE INDEX idx_follows_member_instructor_id
    ON follows (member_instructor_id);

-- 회원별 리뷰 조회
CREATE INDEX idx_reviews_member_id
    ON reviews (member_id);

-- 회원별 주문 조회
CREATE INDEX idx_orders_member_id
    ON orders (member_id);

-- 주문별 출석 조회
CREATE INDEX idx_attendances_order_id
    ON attendances (order_id);
