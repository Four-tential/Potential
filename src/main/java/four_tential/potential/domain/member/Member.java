package four_tential.potential.domain.member;

import four_tential.potential.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "members", uniqueConstraints = {
        @UniqueConstraint(name = "uk_members_email", columnNames = {"email"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

    @Enumerated(EnumType.STRING)
    private MemberStatus status;

    @Column(nullable = false, length = 60)
    private String name;

    @Setter
    @Column(name = "pf_image_url", length = 300)
    private String profileImageUrl;

    @Column(name = "withdrawal_at")
    private LocalDateTime withdrawalAt;

    public static Member register(String email, String password, String phone, String name) {
        Member member = new Member();
        member.email = email;
        member.password = password;
        member.phone = phone;
        member.name = name;
        member.role = MemberRole.ROLE_STUDENT;
        member.status = MemberStatus.ACTIVE;
        member.profileImageUrl = null;
        return member;
    }

    public void activate() {
        this.status = MemberStatus.ACTIVE;
    }

    public void suspend() {
        this.status = MemberStatus.SUSPENDED;
    }

    public void withdraw() {
        this.status = MemberStatus.WITHDRAWAL;
        this.withdrawalAt = LocalDateTime.now();
    }
}
