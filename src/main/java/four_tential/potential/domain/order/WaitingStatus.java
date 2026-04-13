package four_tential.potential.domain.order;

import lombok.Getter;

@Getter
public enum WaitingStatus {
    PENDING("대기 중"),
    CALLED("승격됨"),
    EXPIRED("만료됨");

    private final String description;

    WaitingStatus(String description) {
        this.description = description;
    }
}
