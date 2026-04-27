-- 주문 도메인 성능 최적화를 위한 인덱스 추가
-- 1. 내 주문 목록 조회 (member_id, created_at DESC)
-- Pageable 정렬 조건(createdAt DESC, id DESC)과 일치시켜 정렬 비용 제거
CREATE INDEX idx_orders_member_created ON orders (member_id, created_at DESC, id DESC);

-- 2. 코스별 주문 현황 및 학생 목록 조회 (course_id, status)
CREATE INDEX idx_orders_course_status ON orders (course_id, status);

-- 3. 주문 만료 배치 처리 (status, expire_at)
CREATE INDEX idx_orders_status_expire ON orders (status, expire_at);
