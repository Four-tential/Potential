package four_tential.potential.domain.member.follow;

import four_tential.potential.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Getter
@Entity
@Table(
        name = "follows",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_follows_member_instructor",
                columnNames = {"member_id", "member_instuctor_id"}
        )
)
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Follow extends BaseTimeEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "member_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID memberId;

    @Column(name = "member_instuctor_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID memberInstructorId;

    public static Follow register(UUID memberId, UUID memberInstructorId) {
        Follow follow = new Follow();
        follow.memberId = memberId;
        follow.memberInstructorId = memberInstructorId;
        return follow;
    }
}
