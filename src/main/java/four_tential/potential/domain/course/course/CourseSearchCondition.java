package four_tential.potential.domain.course.course;

import java.math.BigInteger;

public record CourseSearchCondition(
        String categoryCode,
        CourseStatus status,
        CourseLevel level,
        String keyword,
        BigInteger minPrice,
        BigInteger maxPrice,
        CourseSort sort
) {
}
