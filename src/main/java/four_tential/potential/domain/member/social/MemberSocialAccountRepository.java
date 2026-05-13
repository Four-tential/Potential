package four_tential.potential.domain.member.social;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MemberSocialAccountRepository extends JpaRepository<MemberSocialAccount, UUID> {

    Optional<MemberSocialAccount> findByProviderAndProviderId(SocialProvider provider, String providerId);

    boolean existsByMemberIdAndProvider(UUID memberId, SocialProvider provider);

    List<MemberSocialAccount> findAllByMemberId(UUID memberId);

    void deleteByMemberIdAndProvider(UUID memberId, SocialProvider provider);
}
