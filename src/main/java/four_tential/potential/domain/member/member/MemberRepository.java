package four_tential.potential.domain.member.member;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MemberRepository extends JpaRepository<Member, UUID> {
    boolean existsByEmail(String email);
    Optional<Member> findByEmail(String email);

    List<Member> findAllByEmailStartingWith(String prefix);

    @Modifying
    @Query("delete from Member m where m.email like :prefix%")
    void deleteByEmailStartingWith(@Param("prefix") String prefix);
}
