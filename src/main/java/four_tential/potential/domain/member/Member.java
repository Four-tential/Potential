package four_tential.potential.domain.member;

import four_tential.potential.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "members", uniqueConstraints = {
        @UniqueConstraint(name = "uk_members_id", columnNames = {"id"})
})
public class Member extends BaseTimeEntity {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(nullable = false, length = 100)
    private String password;

    @Column(nullable = false, length = 40)
    private String phone;

    @Enumerated(EnumType.STRING)
    private MemberRole role;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(name = "pf_image_url", nullable = false, length = 300)
    private String profileImageUrl;

    @Column(name = "withdrawal_at")
    private LocalDateTime withdrawalAt;
}
