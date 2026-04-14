package four_tential.potential.domain.member.onboard_category;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.member.fixture.MemberFixture;
import four_tential.potential.domain.member.fixture.OnBoardCategoryFixture;
import four_tential.potential.domain.member.member.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OnBoardCategoryTest {

    @Test
    @DisplayName("register()로 생성하면 member와 categoryCode가 설정")
    void register() {
        Member member = MemberFixture.defaultMember();

        OnBoardCategory category = OnBoardCategory.register(member, "COOK");

        assertThat(category.getMember()).isEqualTo(member);
        assertThat(category.getCategoryCode()).isEqualTo("COOK");
    }

    @Test
    @DisplayName("생성된 카테고리는 id가 null")
    void registerInitialState() {
        OnBoardCategory category = OnBoardCategoryFixture.defaultOnBoardCategory();

        assertThat(category.getId()).isNull();
    }

    @Test
    @DisplayName("배열로 전달된 categoryCode 목록으로 여러 행을 생성할 수 있는지 체크")
    void registerMultipleCategories() {
        Member member = MemberFixture.defaultMember();
        List<String> categoryCodes = List.of("ART", "BOOK", "COOK");

        List<OnBoardCategory> categories = categoryCodes.stream()
                .map(code -> OnBoardCategory.register(member, code))
                .toList();

        assertThat(categories).hasSize(3);
        assertThat(categories)
                .extracting(onBoardCategory -> onBoardCategory.getCategoryCode())
                .containsExactly("ART", "BOOK", "COOK");
        assertThat(categories)
                .extracting(onBoardCategory -> onBoardCategory.getMember())
                .containsOnly(member);
    }

    @Test
    @DisplayName("register() 시 member가 null이면 ServiceErrorException이 발생")
    void registerWithNullMember() {
        assertThatThrownBy(() -> OnBoardCategory.register(null, "COOK"))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("회원을 입력해주세요");
    }

    @Test
    @DisplayName("register() 시 categoryCode가 null이면 ServiceErrorException이 발생")
    void registerWithNullCategoryCode() {
        Member member = MemberFixture.defaultMember();

        assertThatThrownBy(() -> OnBoardCategory.register(member, null))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("카테고리를 입력해주세요");
    }

    @Test
    @DisplayName("register() 시 categoryCode가 공백이면 ServiceErrorException이 발생")
    void registerWithBlankCategoryCode() {
        Member member = MemberFixture.defaultMember();

        assertThatThrownBy(() -> OnBoardCategory.register(member, ""))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("카테고리를 입력해주세요");
    }
}
