package four_tential.potential.common.entity;

import four_tential.potential.domain.member.MemberStatus;
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

        LocalDateTime beforeDelete = LocalDateTime.now();
        entity.delete();
        LocalDateTime afterDelete = LocalDateTime.now();

        assertThat(entity.isDeleted()).isTrue();
        assertThat(entity.getDeletedAt()).isNotNull();
        assertThat(entity.getDeletedAt()).isAfterOrEqualTo(beforeDelete).isBeforeOrEqualTo(afterDelete);
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

    @Test
    @DisplayName("MemberStatus의 모든 값이 존재 체크")
    void values() {
        assertThat(MemberStatus.values())
                .containsExactly(
                        MemberStatus.ACTIVE,
                        MemberStatus.SUSPENDED,
                        MemberStatus.WITHDRAWAL
                );
    }

    @Test
    @DisplayName("문자열로 MemberStatus를 조회할 수 있는지 체크")
    void valueOf() {
        assertThat(MemberStatus.valueOf("ACTIVE")).isEqualTo(MemberStatus.ACTIVE);
        assertThat(MemberStatus.valueOf("SUSPENDED")).isEqualTo(MemberStatus.SUSPENDED);
        assertThat(MemberStatus.valueOf("WITHDRAWAL")).isEqualTo(MemberStatus.WITHDRAWAL);
    }
}
