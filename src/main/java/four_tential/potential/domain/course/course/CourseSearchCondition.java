package four_tential.potential.domain.course.course;

import java.math.BigInteger;
import java.util.UUID;

public record CourseSearchCondition(
        String categoryCode,
        CourseStatus status,
        CourseLevel level,
        String keyword,
        BigInteger minPrice,
        BigInteger maxPrice,
        CourseSort sort,
        UUID cursorId
) {
}
