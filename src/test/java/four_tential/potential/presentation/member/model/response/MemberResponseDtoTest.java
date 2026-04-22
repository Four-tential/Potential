package four_tential.potential.presentation.member.model.response;

import four_tential.potential.domain.member.member.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MemberResponseDtoTest {

    private Member createMember() {
        return Member.register("test@example.com", "password123!", "홍길동", "010-1234-5678");
    }

    @Test
    @DisplayName("MyPageResponse.register는 Member 정보를 올바르게 매핑한다")
    void myPageResponse_register() {
        Member member = createMember();
        String profileImageUrl = "https://cdn.example.com/profile.jpg";

        MyPageResponse response = MyPageResponse.register(member, profileImageUrl);

        assertThat(response.email()).isEqualTo("test@example.com");
        assertThat(response.name()).isEqualTo("홍길동");
        assertThat(response.phone()).isEqualTo("010-1234-5678");
        assertThat(response.role()).isEqualTo("ROLE_STUDENT");
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.profileImageUrl()).isEqualTo(profileImageUrl);
    }

    @Test
    @DisplayName("UpdateMyPageResponse.register는 Member 정보를 올바르게 매핑한다")
    void updateMyPageResponse_register() {
        Member member = createMember();
        String profileImageUrl = "https://cdn.example.com/new-profile.jpg";

        UpdateMyPageResponse response = UpdateMyPageResponse.register(member, profileImageUrl);

        assertThat(response.name()).isEqualTo("홍길동");
        assertThat(response.phone()).isEqualTo("010-1234-5678");
        assertThat(response.profileImageUrl()).isEqualTo(profileImageUrl);
    }

    @Test
    @DisplayName("ChangeMemberStatusResponse.register는 Member 상태를 올바르게 매핑한다")
    void changeMemberStatusResponse_register() {
        Member member = createMember();

        ChangeMemberStatusResponse response = ChangeMemberStatusResponse.register(member);

        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("FollowResponse.register는 팔로우 상태를 올바르게 매핑한다")
    void followResponse_register() {
        UUID instructorId = UUID.randomUUID();

        FollowResponse response = FollowResponse.register(instructorId, true);

        assertThat(response.instructorId()).isEqualTo(instructorId);
        assertThat(response.isFollowed()).isTrue();
    }

    @Test
    @DisplayName("FollowResponse.register - 팔로우 해제 상태")
    void followResponse_register_unfollowed() {
        UUID instructorId = UUID.randomUUID();

        FollowResponse response = FollowResponse.register(instructorId, false);

        assertThat(response.isFollowed()).isFalse();
    }

    @Test
    @DisplayName("WishlistCourseItem.register는 쿼리 결과를 올바르게 매핑한다")
    void wishlistCourseItem_register() {
        var result = new four_tential.potential.domain.course.course_wishlist.WishlistCourseQueryResult(
                UUID.randomUUID(), "초급 요가", "김강사", "https://cdn.example.com/thumb.jpg",
                "YOGA", "요가", java.math.BigInteger.valueOf(120000),
                four_tential.potential.domain.course.course.CourseStatus.OPEN,
                java.time.LocalDateTime.of(2025, 6, 1, 10, 0),
                java.time.LocalDateTime.of(2025, 5, 20, 14, 30)
        );

        WishlistCourseItem item = WishlistCourseItem.register(result);

        assertThat(item.title()).isEqualTo("초급 요가");
        assertThat(item.memberInstructorName()).isEqualTo("김강사");
        assertThat(item.categoryCode()).isEqualTo("YOGA");
        assertThat(item.price()).isEqualTo(java.math.BigInteger.valueOf(120000));
    }
}
