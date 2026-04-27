-- =====================================================
-- 출석 조회 성능 개선 - 복합 인덱스 추가
--
-- 대상 쿼리:
--   SELECT * FROM attendances
--   WHERE member_id = ? AND course_id = ?
--   (FOR UPDATE 포함)
--
-- 개선 효과:
--   - member_id + course_id Full Scan → Index Scan 전환
--   - 비관적 락(FOR UPDATE) 시 테이블 락 에스컬레이션 방지
--     (인덱스가 없으면 MySQL이 행 락 대신 테이블 락으로 확장될 수 있음)
-- =====================================================

CREATE INDEX idx_attendances_member_course
    ON attendances (member_id, course_id);