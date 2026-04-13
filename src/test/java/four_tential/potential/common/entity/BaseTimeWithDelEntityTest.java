package four_tential.potential.common.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class BaseTimeWithDelEntityTest {

    // 테스트용 구체 클래스
    static class TestEntity extends BaseTimeWithDelEntity {
        TestEntity() {
            super();
        }
    }

    @Test
    @DisplayName("delete()를 호출하면 deleted가 true가 되고 deletedAt 설정")
    void delete() {
        TestEntity entity = new TestEntity();
        assertThat(entity.isDeleted()).isFalse();
        assertThat(entity.getDeletedAt()).isNull();

        entity.delete();

        assertThat(entity.isDeleted()).isTrue();
        assertThat(entity.getDeletedAt()).isNotNull();
        assertThat(entity.getDeletedAt()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    @DisplayName("restore()를 호출하면 deleted가 false로 돌아오고 deletedAt이 null")
    void restore() {
        TestEntity entity = new TestEntity();
        entity.delete();

        entity.restore();

        assertThat(entity.isDeleted()).isFalse();
        assertThat(entity.getDeletedAt()).isNull();
    }
}
