package four_tential.potential.domain.member.onboard_category;

import four_tential.potential.common.entity.BaseTimeEntity;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.MemberExceptionEnum;
import four_tential.potential.domain.member.member.Member;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Getter
@Entity
@Table(name = "onboard_categories", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_onboard_categories_member_category",
                columnNames = {"member_id", "category_code"}
        )
})
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class OnBoardCategory extends BaseTimeEntity {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false, columnDefinition = "BINARY(16)")
    private Member member;

    @Column(name = "category_code", nullable = false, length = 30)
    private String categoryCode;

    public static OnBoardCategory register(Member member, String categoryCode) {
        if (member == null) {
            throw new ServiceErrorException(MemberExceptionEnum.ERR_INVALID_MEMBER);
        }

        if (categoryCode == null || categoryCode.isBlank()) {
            throw new ServiceErrorException(MemberExceptionEnum.ERR_INVALID_CATEGORY);
        }

        OnBoardCategory onBoardCategory = new OnBoardCategory();
        onBoardCategory.member = member;
        onBoardCategory.categoryCode = categoryCode;
        return onBoardCategory;
    }
}
