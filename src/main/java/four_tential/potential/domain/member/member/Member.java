package four_tential.potential.domain.member.member;

import four_tential.potential.common.entity.BaseTimeEntity;
import four_tential.potential.common.exception.ServiceErrorException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import static four_tential.potential.common.exception.domain.MemberExceptionEnum.ERR_INVALID_MEMBER_STATUS_TRANSITION;
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
    @Column(nullable = false, length = 30)
    private MemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MemberStatus status;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false)
    private boolean hasOnboarding;

    @Setter
    @Column(name = "pf_image_url", length = 300)
    private String profileImageUrl;

    @Column(name = "withdrawal_at")
    private LocalDateTime withdrawalAt;

    public static Member register(String email, String password, String name, String phone) {
        Member member = new Member();
        member.email = email;
        member.password = password;
        member.name = name;
        member.phone = phone;
        member.hasOnboarding = false;
        member.role = MemberRole.ROLE_STUDENT;
        member.status = MemberStatus.ACTIVE;
        return member;
    }

    public void completeOnboarding() {
        this.hasOnboarding = true;
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

    public void promoteToInstructor() {
        this.role = MemberRole.ROLE_INSTRUCTOR;
    }

    // 관리자가 회원 상태 변경 (ACTIVE ↔ SUSPENDED 전환만 허용)
    public void changeStatus(MemberStatus newStatus) {
        boolean validTransition =
                (this.status == MemberStatus.ACTIVE && newStatus == MemberStatus.SUSPENDED) ||
                (this.status == MemberStatus.SUSPENDED && newStatus == MemberStatus.ACTIVE);

        if (!validTransition) {
            throw new ServiceErrorException(ERR_INVALID_MEMBER_STATUS_TRANSITION);
        }
        this.status = newStatus;
    }

    public void changePassword(String encodedNewPassword) {
        this.password = encodedNewPassword;
    }

    public void updateInfo(String phone, String profileImageUrl) {
        if (phone != null) {
            this.phone = phone;
        }

        if (profileImageUrl != null) {
            this.profileImageUrl = profileImageUrl;
        }
    }
}
