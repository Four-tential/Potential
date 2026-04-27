CREATE INDEX idx_attendances_member_course
    ON attendances (member_id, course_id);