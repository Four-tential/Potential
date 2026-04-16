package four_tential.potential.presentation.member;

import four_tential.potential.application.member.MemberService;
import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.member.fixture.MemberFixture;
import four_tential.potential.domain.member.member_onboard.MemberOnBoardGoal;
import four_tential.potential.infra.security.principal.MemberPrincipal;
import four_tential.potential.presentation.member.model.request.OnBoardRequest;
import four_tential.potential.presentation.member.model.request.UpdateMyPageRequest;
import four_tential.potential.presentation.member.model.request.UpdateOnBoardRequest;
import four_tential.potential.presentation.member.model.response.MyPageResponse;
import four_tential.potential.presentation.member.model.response.OnBoardResponse;
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
import java.util.List;
import java.util.UUID;

import static four_tential.potential.common.exception.domain.CourseExceptionEnum.ERR_NOT_FOUND_CATEGORY;
import static four_tential.potential.common.exception.domain.MemberExceptionEnum.*;
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
        assertThat(response.getBody()).isNotNull();
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
        assertThat(response.getBody()).isNotNull();
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

    // region registerOnBoarding
    @Test
    @DisplayName("온보딩 등록 - 201 CREATED 및 등록 정보 반환")
    void registerOnBoarding_success() {
        List<String> categoryCodes = List.of("FITNESS", "ART");
        OnBoardRequest request = new OnBoardRequest(MemberOnBoardGoal.HOBBY, categoryCodes);
        OnBoardResponse serviceResponse = new OnBoardResponse("HOBBY", categoryCodes, LocalDateTime.now());
        given(memberService.createOnBoarding(MEMBER_ID, request)).willReturn(serviceResponse);

        ResponseEntity<BaseResponse<OnBoardResponse>> response = memberController.registerOnBoarding(request, PRINCIPAL);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().goal()).isEqualTo("HOBBY");
        assertThat(response.getBody().data().categoryCodes()).containsExactly("FITNESS", "ART");
    }

    @Test
    @DisplayName("온보딩 등록 - 이미 온보딩 완료 회원이면 ServiceErrorException 전파")
    void registerOnBoarding_alreadyOnboarded() {
        OnBoardRequest request = new OnBoardRequest(MemberOnBoardGoal.HOBBY, List.of("FITNESS"));
        given(memberService.createOnBoarding(MEMBER_ID, request))
                .willThrow(new ServiceErrorException(ERR_ALREADY_ONBOARDED));

        assertThatThrownBy(() -> memberController.registerOnBoarding(request, PRINCIPAL))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("이미 온보딩을 완료한 회원입니다");
    }

    @Test
    @DisplayName("온보딩 등록 - 존재하지 않는 카테고리 코드면 ServiceErrorException 전파")
    void registerOnBoarding_invalidCategory() {
        OnBoardRequest request = new OnBoardRequest(MemberOnBoardGoal.HOBBY, List.of("INVALID"));
        given(memberService.createOnBoarding(MEMBER_ID, request))
                .willThrow(new ServiceErrorException(ERR_NOT_FOUND_CATEGORY));

        assertThatThrownBy(() -> memberController.registerOnBoarding(request, PRINCIPAL))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 카테고리입니다");
    }
    // endregion

    // region updateOnBoarding
    @Test
    @DisplayName("온보딩 수정 - 200 OK 및 변경된 정보 반환 (목표 + 카테고리 모두 수정)")
    void updateOnBoarding_success() {
        List<String> categoryCodes = List.of("COOK");
        UpdateOnBoardRequest request = new UpdateOnBoardRequest(MemberOnBoardGoal.STRESS_OUT, categoryCodes);
        OnBoardResponse serviceResponse = new OnBoardResponse("STRESS_OUT", categoryCodes, LocalDateTime.now());
        given(memberService.updateOnBoarding(MEMBER_ID, request)).willReturn(serviceResponse);

        ResponseEntity<BaseResponse<OnBoardResponse>> response = memberController.updateOnBoarding(request, PRINCIPAL);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().goal()).isEqualTo("STRESS_OUT");
        assertThat(response.getBody().data().categoryCodes()).containsExactly("COOK");
    }

    @Test
    @DisplayName("온보딩 수정 - 200 OK 및 목표만 수정 시 응답 반환")
    void updateOnBoarding_goalOnly() {
        UpdateOnBoardRequest request = new UpdateOnBoardRequest(MemberOnBoardGoal.STRESS_OUT, null);
        OnBoardResponse serviceResponse = new OnBoardResponse("STRESS_OUT", List.of("FITNESS"), LocalDateTime.now());
        given(memberService.updateOnBoarding(MEMBER_ID, request)).willReturn(serviceResponse);

        ResponseEntity<BaseResponse<OnBoardResponse>> response = memberController.updateOnBoarding(request, PRINCIPAL);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().goal()).isEqualTo("STRESS_OUT");
    }

    @Test
    @DisplayName("온보딩 수정 - 모든 필드 null이면 ServiceErrorException 전파")
    void updateOnBoarding_allNull() {
        UpdateOnBoardRequest request = new UpdateOnBoardRequest(null, null);
        given(memberService.updateOnBoarding(MEMBER_ID, request))
                .willThrow(new ServiceErrorException(ERR_NO_UPDATE_FIELD));

        assertThatThrownBy(() -> memberController.updateOnBoarding(request, PRINCIPAL))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("수정할 항목을 하나 이상 입력해주세요");
    }

    @Test
    @DisplayName("온보딩 수정 - 온보딩 미설정 회원이면 ServiceErrorException 전파")
    void updateOnBoarding_notFound() {
        UpdateOnBoardRequest request = new UpdateOnBoardRequest(MemberOnBoardGoal.STRESS_OUT, List.of("COOK"));
        given(memberService.updateOnBoarding(MEMBER_ID, request))
                .willThrow(new ServiceErrorException(ERR_NOT_FOUND_ONBOARDING));

        assertThatThrownBy(() -> memberController.updateOnBoarding(request, PRINCIPAL))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("온보딩 정보가 존재하지 않습니다");
    }

    @Test
    @DisplayName("온보딩 수정 - 존재하지 않는 카테고리 코드면 ServiceErrorException 전파")
    void updateOnBoarding_invalidCategory() {
        UpdateOnBoardRequest request = new UpdateOnBoardRequest(MemberOnBoardGoal.STRESS_OUT, List.of("INVALID"));
        given(memberService.updateOnBoarding(MEMBER_ID, request))
                .willThrow(new ServiceErrorException(ERR_NOT_FOUND_CATEGORY));

        assertThatThrownBy(() -> memberController.updateOnBoarding(request, PRINCIPAL))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 카테고리입니다");
    }
    // endregion
}
