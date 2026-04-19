package four_tential.potential.domain.member.follow;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface FollowQueryRepository {
    Page<FollowQueryResult> findFollowedInstructors(UUID followerId, Pageable pageable);
}
