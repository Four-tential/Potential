package four_tential.potential.infra.s3;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ImageType {
    PROFILE("profile-image"),
    INSTRUCTOR("instructor-image"),
    COURSE("course-image"),
    REVIEW("review-image");

    private final String prefix;
}
