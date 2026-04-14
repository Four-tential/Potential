package four_tential.potential.domain.member.member_onboard;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MemberOnBoardRepository extends JpaRepository<MemberOnBoard, UUID> {
}
