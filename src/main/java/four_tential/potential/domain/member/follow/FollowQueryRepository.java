package four_tential.potential.domain.member.follow;

import four_tential.potential.presentation.member.model.response.FollowedInstructorItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface FollowQueryRepository {
    Page<FollowedInstructorItem> findFollowedInstructors(UUID followerId, Pageable pageable);
}
