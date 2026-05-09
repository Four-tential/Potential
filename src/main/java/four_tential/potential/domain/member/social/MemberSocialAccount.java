package four_tential.potential.domain.member.social;

import four_tential.potential.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Getter
@Entity
@Table(
        name = "member_social_accounts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_msa_provider_provider_id", columnNames = {"provider", "provider_id"}),
                @UniqueConstraint(name = "uk_msa_member_provider", columnNames = {"member_id", "provider"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberSocialAccount extends BaseTimeEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "member_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SocialProvider provider;

    @Column(name = "provider_id", nullable = false, length = 255)
    private String providerId;

    @Column(length = 255)
    private String email;

    public static MemberSocialAccount link(UUID memberId, SocialProvider provider, String providerId, String email) {
        MemberSocialAccount account = new MemberSocialAccount();
        account.memberId = memberId;
        account.provider = provider;
        account.providerId = providerId;
        account.email = email;
        return account;
    }
}
