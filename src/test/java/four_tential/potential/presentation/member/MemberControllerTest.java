package four_tential.potential.presentation.member;

import four_tential.potential.application.member.MemberService;
import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.member.fixture.MemberFixture;
import four_tential.potential.infra.security.principal.MemberPrincipal;
import four_tential.potential.presentation.member.model.request.UpdateMyPageRequest;
import four_tential.potential.presentation.member.model.response.MyPageResponse;
import four_tential.potential.presentation.member.model.response.UpdateMyPageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.UUID;

import static four_tential.potential.common.exception.domain.MemberExceptionEnum.ERR_NOT_FOUND_MEMBER;
import static four_tential.potential.common.exception.domain.MemberExceptionEnum.ERR_NO_UPDATE_FIELD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MemberControllerTest {

    @Mock
    private MemberService memberService;

    @InjectMocks
    private MemberController memberController;

    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final MemberPrincipal PRINCIPAL = new MemberPrincipal(MEMBER_ID, MemberFixture.DEFAULT_EMAIL, "ROLE_STUDENT");

    // region getMyPageInfo
    @Test
    @DisplayName("마이페이지 조회 - 200 OK 및 회원 정보 반환")
    void getMyPageInfo_success() {
        MyPageResponse serviceResponse = new MyPageResponse(
                MEMBER_ID,
                MemberFixture.DEFAULT_EMAIL,
                MemberFixture.DEFAULT_NAME,
                MemberFixture.DEFAULT_PHONE,
                "ROLE_STUDENT",
                "ACTIVE",
                MemberFixture.DEFAULT_PROFILE_IMAGE_URL,
                LocalDateTime.now()
        );
        given(memberService.getMyPageInfo(MEMBER_ID)).willReturn(serviceResponse);

        ResponseEntity<BaseResponse<MyPageResponse>> response = memberController.getMyPageInfo(PRINCIPAL);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assert response.getBody() != null;
        assertThat(response.getBody().data().email()).isEqualTo(MemberFixture.DEFAULT_EMAIL);
        assertThat(response.getBody().data().profileImageUrl()).isEqualTo(MemberFixture.DEFAULT_PROFILE_IMAGE_URL);
    }

    @Test
    @DisplayName("마이페이지 조회 - 존재하지 않는 회원이면 ServiceErrorException 전파")
    void getMyPageInfo_memberNotFound() {
        given(memberService.getMyPageInfo(MEMBER_ID)).willThrow(new ServiceErrorException(ERR_NOT_FOUND_MEMBER));

        assertThatThrownBy(() -> memberController.getMyPageInfo(PRINCIPAL))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 회원입니다");
    }
    // endregion

    // region updateMyPageInfo
    @Test
    @DisplayName("마이페이지 수정 - 200 OK 및 수정된 정보 반환")
    void updateMyPageInfo_success() {
        UpdateMyPageRequest request = new UpdateMyPageRequest("010-9999-9999", null);
        UpdateMyPageResponse serviceResponse = new UpdateMyPageResponse(
                MEMBER_ID,
                MemberFixture.DEFAULT_NAME,
                "010-9999-9999",
                MemberFixture.DEFAULT_PROFILE_IMAGE_URL,
                LocalDateTime.now()
        );
        given(memberService.updateMyPageInfo(MEMBER_ID, request)).willReturn(serviceResponse);

        ResponseEntity<BaseResponse<UpdateMyPageResponse>> response = memberController.updateMyPageInfo(request, PRINCIPAL);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assert response.getBody() != null;
        assertThat(response.getBody().data().phone()).isEqualTo("010-9999-9999");
    }

    @Test
    @DisplayName("마이페이지 수정 - 모든 필드 null이면 ServiceErrorException 전파")
    void updateMyPageInfo_allNull() {
        UpdateMyPageRequest request = new UpdateMyPageRequest(null, null);
        given(memberService.updateMyPageInfo(MEMBER_ID, request))
                .willThrow(new ServiceErrorException(ERR_NO_UPDATE_FIELD));

        assertThatThrownBy(() -> memberController.updateMyPageInfo(request, PRINCIPAL))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("수정할 항목을 하나 이상 입력해주세요");
    }

    @Test
    @DisplayName("마이페이지 수정 - 존재하지 않는 회원이면 ServiceErrorException 전파")
    void updateMyPageInfo_memberNotFound() {
        UpdateMyPageRequest request = new UpdateMyPageRequest("010-9999-9999", null);
        given(memberService.updateMyPageInfo(MEMBER_ID, request))
                .willThrow(new ServiceErrorException(ERR_NOT_FOUND_MEMBER));

        assertThatThrownBy(() -> memberController.updateMyPageInfo(request, PRINCIPAL))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 회원입니다");
    }
    // endregion
}
