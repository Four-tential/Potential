package four_tential.potential.domain.payment.entity;

import four_tential.potential.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Getter
@Entity
@Table(
        name = "refund_outbox",
        indexes = {
                @Index(name = "idx_refund_outbox_status", columnList = "status"),
                @Index(name = "idx_refund_outbox_course_id", columnList = "course_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefundOutbox extends BaseTimeEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    // Todo: 환불 아웃박스 추가 작성

}
