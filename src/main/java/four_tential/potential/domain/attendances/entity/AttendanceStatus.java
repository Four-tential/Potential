package four_tential.potential.domain.attendances.entity;

import lombok.Getter;

@Getter
public enum AttendanceStatus {
    ATTEND("출석"),
    ABSENT("결석");

    private final String description;
    AttendanceStatus(String description) {
        this.description = description;
    }
}
