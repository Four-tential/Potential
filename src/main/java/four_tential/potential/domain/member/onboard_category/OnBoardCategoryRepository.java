package four_tential.potential.domain.member.onboard_category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface OnBoardCategoryRepository extends JpaRepository<MemberOnBoardCategory, UUID> {
    List<MemberOnBoardCategory> findByMemberId(UUID memberId);
    void deleteByMemberIdAndCategoryCodeIn(UUID memberId, Set<String> categoryCodes);
}
