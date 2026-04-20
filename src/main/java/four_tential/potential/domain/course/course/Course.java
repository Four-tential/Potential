package four_tential.potential.domain.course.course;

import four_tential.potential.common.entity.BaseTimeWithDelEntity;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.course.course_image.CourseImage;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static four_tential.potential.common.exception.domain.CourseExceptionEnum.*;

@Getter
@Entity
@Table(name = "courses")
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Course extends BaseTimeWithDelEntity {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "course_category_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID courseCategoryId;

    @Column(name = "member_instructor_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID memberInstructorId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "address_main", nullable = false, length = 300)
    private String addressMain;

    @Column(name = "address_detail", nullable = false, length = 300)
    private String addressDetail;

    @Column(nullable = false)
    private int capacity;

    @Column(name = "confirm_count", nullable = false)
    private int confirmCount;

    @Column(nullable = false)
    private BigInteger price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CourseLevel level;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CourseStatus status;

    @Column(name = "order_open_at", nullable = false)
    private LocalDateTime orderOpenAt;

    @Column(name = "order_close_at", nullable = false)
    private LocalDateTime orderCloseAt;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CourseImage> images = new ArrayList<>();

    // 관리자 승인일시와 코스의 주문 가능, 마감 시간의 연관을 확실히 정해봐야할 듯
    public static Course register(
            UUID courseCategoryId,
            UUID memberInstructorId,
            String title,
            String description,
            String addressMain,
            String addressDetail,
            int capacity,
            BigInteger price,
            CourseLevel level,
            LocalDateTime orderOpenAt,
            LocalDateTime orderCloseAt,
            LocalDateTime startAt,
            LocalDateTime endAt
    ) {
        // 정원 1명 이상이여야 함
        if (capacity < 1) {
            throw new ServiceErrorException(ERR_INVALID_CAPACITY);
        }

        // 코스의 주문 마감 시간은 코스의 주문가능 시작 시각부터 코스의 시작일시 2시간 전 까지 가능
        if (!orderCloseAt.isAfter(orderOpenAt) || !orderCloseAt.isBefore(startAt.minusHours(2))) {
            throw new ServiceErrorException(ERR_INVALID_ORDER_CLOSE_TIME);
        }

        // 코스 시작일시는 종료일시 후여야 함
        if (!endAt.isAfter(startAt)) {
            throw new ServiceErrorException(ERR_INVALID_SCHEDULE);
        }

        Course course = new Course();
        course.courseCategoryId = courseCategoryId;
        course.memberInstructorId = memberInstructorId;
        course.title = title;
        course.description = description;
        course.addressMain = addressMain;
        course.addressDetail = addressDetail;
        course.capacity = capacity;
        course.confirmCount = 0;
        course.price = price;
        course.level = level;
        course.status = CourseStatus.PREPARATION;
        course.orderOpenAt = orderOpenAt;
        course.orderCloseAt = orderCloseAt;
        course.startAt = startAt;
        course.endAt = endAt;
        return course;
    }

    // PREPARATION, REJECTED, OPEN 모두 가능한 필드들 (제목, 설명)
    public void updateInfo(String title, String description) {
        if (this.status == CourseStatus.CLOSED || this.status == CourseStatus.CANCELLED) {
            throw new ServiceErrorException(ERR_CANNOT_MODIFY_COURSE);
        }
        this.title = title;
        this.description = description;
    }

    // PREPARATION, REJECTED 에서만 가능한 수정필드들 (가격, 일정, 장소, 정원, 레벨)
    public void updateInfoInPreparation(
            BigInteger price,
            Integer capacity,
            CourseLevel level,
            String addressMain,
            String addressDetail,
            LocalDateTime orderOpenAt,
            LocalDateTime orderCloseAt,
            LocalDateTime startAt,
            LocalDateTime endAt
    ) {
        if (this.status != CourseStatus.PREPARATION && this.status != CourseStatus.REJECTED) {
            throw new ServiceErrorException(ERR_IMMUTABLE_FIELD_IN_OPEN);
        }

        if (capacity == null || capacity < 1) {
            throw new ServiceErrorException(ERR_INVALID_CAPACITY);
        }

        if (!orderCloseAt.isAfter(orderOpenAt) || !orderCloseAt.isBefore(startAt.minusHours(2))) {
            throw new ServiceErrorException(ERR_INVALID_ORDER_CLOSE_TIME);
        }

        if (!endAt.isAfter(startAt)) {
            throw new ServiceErrorException(ERR_INVALID_SCHEDULE);
        }

        this.price = price;
        this.capacity = capacity;
        this.level = level;
        this.addressMain = addressMain;
        this.addressDetail = addressDetail;
        this.orderOpenAt = orderOpenAt;
        this.orderCloseAt = orderCloseAt;
        this.startAt = startAt;
        this.endAt = endAt;
    }

    public void confirm() {
        // 코스의 준비 상태 일때만 개설 확정 후 오픈 할 수 있다
        if (this.status != CourseStatus.PREPARATION) {
            throw new ServiceErrorException(ERR_INVALID_STATUS_TRANSITION_TO_CONFIRM);
        }

        this.status = CourseStatus.OPEN;
        this.confirmedAt = LocalDateTime.now();
    }

    public void open() {
        if (this.status != CourseStatus.PREPARATION) {
            throw new ServiceErrorException(ERR_INVALID_STATUS_TRANSITION_TO_OPEN);
        }

        this.status = CourseStatus.OPEN;
    }

    public void close() {
        if (this.status != CourseStatus.OPEN) {
            throw new ServiceErrorException(ERR_INVALID_STATUS_TRANSITION_TO_CLOSE);
        }

        this.status = CourseStatus.CLOSED;
    }

    public void cancel() {
        if (this.status != CourseStatus.OPEN) {
            throw new ServiceErrorException(ERR_INVALID_STATUS_TRANSITION_TO_CANCEL);
        }

        this.status = CourseStatus.CANCELLED;
    }

    public void reject(String rejectReason) {
        if (this.status != CourseStatus.PREPARATION) {
            throw new ServiceErrorException(ERR_COURSE_NOT_IN_PREPARATION);
        }
        if (rejectReason == null || rejectReason.isBlank()) {
            throw new ServiceErrorException(ERR_REJECT_REASON_REQUIRED);
        }
        this.status = CourseStatus.REJECTED;
        this.rejectReason = rejectReason;
    }

    public void reapply() {
        if (this.status != CourseStatus.REJECTED) {
            throw new ServiceErrorException(ERR_CANNOT_REAPPLY_COURSE);
        }
        this.status = CourseStatus.PREPARATION;
        this.rejectReason = null;
    }

    public void increaseConfirmCount() {
        if (!this.isFull()) {
            this.confirmCount++;
        } else {
            throw new ServiceErrorException(ERR_IS_FULL_CAPACITY);
        }
    }

    public void decreaseConfirmCount() {
        if (this.confirmCount > 0) {
            this.confirmCount--;
        }
    }

    // 정원 측정 판별
    public boolean isFull() {
        return this.confirmCount >= this.capacity;
    }

    // 코스 이미지 전체 삭제
    public void clearImages() {
        this.images.clear();
    }
}
