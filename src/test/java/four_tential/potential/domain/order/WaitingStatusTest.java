package four_tential.potential.domain.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WaitingStatus")
class WaitingStatusTest {

    @Test
    @DisplayName("PENDING 상태의 설명은 '대기 중'이다")
    void pendingDescriptionIsCorrect() {
        assertThat(WaitingStatus.PENDING.getDescription()).isEqualTo("대기 중");
    }

    @Test
    @DisplayName("CALLED 상태의 설명은 '승격됨'이다")
    void calledDescriptionIsCorrect() {
        assertThat(WaitingStatus.CALLED.getDescription()).isEqualTo("승격됨");
    }

    @Test
    @DisplayName("EXPIRED 상태의 설명은 '만료됨'이다")
    void expiredDescriptionIsCorrect() {
        assertThat(WaitingStatus.EXPIRED.getDescription()).isEqualTo("만료됨");
    }

    @Test
    @DisplayName("WaitingStatus는 정확히 3개의 값을 가진다")
    void hasExactlyThreeValues() {
        assertThat(WaitingStatus.values()).hasSize(3);
    }

    @Test
    @DisplayName("모든 WaitingStatus 값을 이름으로 조회할 수 있다")
    void allValuesAreAccessibleByName() {
        assertThat(WaitingStatus.valueOf("PENDING")).isEqualTo(WaitingStatus.PENDING);
        assertThat(WaitingStatus.valueOf("CALLED")).isEqualTo(WaitingStatus.CALLED);
        assertThat(WaitingStatus.valueOf("EXPIRED")).isEqualTo(WaitingStatus.EXPIRED);
    }

    @ParameterizedTest
    @EnumSource(WaitingStatus.class)
    @DisplayName("모든 WaitingStatus 값은 null이 아닌 설명을 가진다")
    void allStatusesHaveNonNullDescription(WaitingStatus status) {
        assertThat(status.getDescription()).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("WaitingStatus 값들의 순서가 올바르다")
    void valuesAreInCorrectOrder() {
        WaitingStatus[] values = WaitingStatus.values();
        assertThat(values[0]).isEqualTo(WaitingStatus.PENDING);
        assertThat(values[1]).isEqualTo(WaitingStatus.CALLED);
        assertThat(values[2]).isEqualTo(WaitingStatus.EXPIRED);
    }

    @Test
    @DisplayName("WaitingStatus PENDING은 대기 중 상태를 나타낸다")
    void pendingRepresentsWaitingState() {
        assertThat(WaitingStatus.PENDING.name()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("WaitingStatus CALLED는 대기 완료(승격) 상태를 나타낸다")
    void calledRepresentsPromotedState() {
        assertThat(WaitingStatus.CALLED.name()).isEqualTo("CALLED");
    }
}