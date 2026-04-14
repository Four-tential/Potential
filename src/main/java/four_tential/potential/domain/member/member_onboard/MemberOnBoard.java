package four_tential.potential.domain.member.member_onboard;

import four_tential.potential.common.entity.BaseTimeEntity;
import four_tential.potential.domain.member.member.Member;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Getter
@Entity
@Table(name = "member_onboards", uniqueConstraints = {
        @UniqueConstraint(name = "uk_member_onboards_member_id", columnNames = {"member_id"})
})
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class MemberOnBoard extends BaseTimeEntity {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false, unique = true, columnDefinition = "BINARY(16)")
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MemberOnBoardGoal goal;

    public static MemberOnBoard register(Member member, MemberOnBoardGoal goal) {
        if (member == null) {
            throw new IllegalArgumentException("회원을 입력해주세요");
        }

        if (goal == null) {
            throw new IllegalArgumentException("목표를 입력해주세요");
        }

        MemberOnBoard memberOnBoard = new MemberOnBoard();
        memberOnBoard.member = member;
        memberOnBoard.goal = goal;
        return memberOnBoard;
    }

    public void updateGoal(MemberOnBoardGoal goal) {
        if (goal == null) {
            throw new IllegalArgumentException("목표를 입력해주세요");
        }

        this.goal = goal;
    }
}

