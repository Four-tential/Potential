package four_tential.potential.presentation.course.model.request;

import four_tential.potential.domain.course.course.CourseLevel;
import four_tential.potential.domain.course.course.CourseStatus;

import java.math.BigInteger;

public record CourseSearchRequest(
        String categoryCode,
        CourseStatus status,
        CourseLevel level,
        String keyword,
        BigInteger minPrice,
        BigInteger maxPrice,
        CourseSort sort
) {
}
