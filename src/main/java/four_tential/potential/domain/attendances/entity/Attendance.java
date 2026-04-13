package four_tential.potential.domain.attendances.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "attendances")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Attendance {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "order_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID orderId;

    @Column(name = "member_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID memberId;

    @Column(name = "course_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID courseId;

    @Column(name = "qr_code", unique = true, columnDefinition = "VARCHAR(300)")
    private String qrCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(30)")
    private AttendanceStatus status;

    @Column(name = "attendance_at")
    private LocalDateTime attendanceAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static Attendance register(UUID orderId, UUID memberId, UUID courseId) {
        Attendance attendance = new Attendance();
        attendance.orderId = orderId;
        attendance.memberId = memberId;
        attendance.courseId = courseId;
        attendance.status = AttendanceStatus.ABSENT;
        return attendance;
    }

    public void attend(String qrCode) {
        this.qrCode = qrCode;
        this.status = AttendanceStatus.ATTEND;
        this.attendanceAt = LocalDateTime.now();
    }
}
