package four_tential.potential.domain.course.course_inventory;

import four_tential.potential.common.entity.BaseTimeEntity;
import four_tential.potential.common.exception.ServiceErrorException;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

import static four_tential.potential.common.exception.domain.CourseExceptionEnum.*;

@Getter
@Entity
@Table(name = "course_inventory")
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class CourseInventory extends BaseTimeEntity {

    @Id
    @Column(name = "course_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID courseId;

    @Column(name = "max_capacity", nullable = false)
    private int maxCapacity;

    @Column(name = "confirm_count", nullable = false)
    private int confirmCount;

    @Version
    @Column(nullable = false)
    private Long version;

    public static CourseInventory register(UUID courseId, int maxCapacity) {
        if (maxCapacity < 1) {
            throw new ServiceErrorException(ERR_INVALID_CAPACITY);
        }

        CourseInventory inventory = new CourseInventory();
        inventory.courseId = courseId;
        inventory.maxCapacity = maxCapacity;
        inventory.confirmCount = 0;
        return inventory;
    }

    // 빈 자리가 count 만큼 있는지 확인
    public boolean hasAvailableSeats(int count) {
        return this.confirmCount + count <= this.maxCapacity;
    }

    // 확정 인원 증가
    public void increase(int count) {
        if (!hasAvailableSeats(count)) {
            throw new ServiceErrorException(ERR_IS_FULL_CAPACITY);
        }
        this.confirmCount += count;
    }

    // 확정 인원 감소
    public void decrease(int count) {
        this.confirmCount = Math.max(0, this.confirmCount - count);
    }

    // 최대 정원 변경 (PREPARATION 상태만 바뀌도록 조치 해야함)
    public void updateMaxCapacity(int maxCapacity) {
        if (maxCapacity < 1) {
            throw new ServiceErrorException(ERR_INVALID_CAPACITY);
        }
        this.maxCapacity = maxCapacity;
    }
}
