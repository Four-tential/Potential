package four_tential.potential.domain.order;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "waiting_list", uniqueConstraints = {
        @UniqueConstraint(name = "uk_waiting_list", columnNames = {"id"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WaitingList {

    public static final int WAITING_LIST_EXPIRATION_MINUTES = 30; // 대기열 만료 시간

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "course_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID courseId;

    @Column(name = "member_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID memberId;

    @Column(name = "wait_number", nullable = false)
    private int waitNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WaitingStatus status;

    @Column(name = "waited_at", nullable = false)
    private LocalDateTime waitedAt;

    @Column(name = "called_at")
    private LocalDateTime calledAt;

    @Column(name = "expired_at", nullable = false)
    private LocalDateTime expiredAt;

    public static WaitingList register(UUID memberId, UUID courseId, int waitNumber) {
        WaitingList waitingList = new WaitingList();
        waitingList.memberId = memberId;
        waitingList.courseId = courseId;
        waitingList.waitNumber = waitNumber;
        waitingList.status = WaitingStatus.WAITING;
        waitingList.waitedAt = LocalDateTime.now();
        waitingList.expiredAt = waitingList.waitedAt.plusMinutes(WAITING_LIST_EXPIRATION_MINUTES);
        return waitingList;
    }
}
