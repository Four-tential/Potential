CREATE INDEX idx_reviews_course_created
    ON reviews (course_id, created_at DESC);