package four_tential.potential.domain.member.onboard_category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OnBoardCategoryRepository extends JpaRepository<OnBoardCategory, UUID> {
}
