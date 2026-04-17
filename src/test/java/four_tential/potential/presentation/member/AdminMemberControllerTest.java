package four_tential.potential.presentation.member;

import four_tential.potential.application.member.MemberService;
import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.member.member.MemberStatus;
import four_tential.potential.presentation.member.model.request.ChangeMemberStatusRequest;
import four_tential.potential.presentation.member.model.response.ChangeMemberStatusResponse;
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

import static four_tential.potential.common.exception.domain.MemberExceptionEnum.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AdminMemberControllerTest {

    @Mock
    private MemberService memberService;

    @InjectMocks
    private MemberController memberController;

    private static final UUID TARGET_MEMBER_ID = UUID.randomUUID();

    // region changeMemberStatus
    @Test
    @DisplayName("회원 상태 변경 - 200 OK 및 변경된 상태 반환 (ACTIVE → SUSPENDED)")
    void changeMemberStatus_activeToSuspended_success() {
        ChangeMemberStatusRequest request = new ChangeMemberStatusRequest(MemberStatus.SUSPENDED);
        ChangeMemberStatusResponse serviceResponse = new ChangeMemberStatusResponse(
                TARGET_MEMBER_ID,
                "SUSPENDED",
                LocalDateTime.now()
        );
        given(memberService.changeMemberStatus(TARGET_MEMBER_ID, request)).willReturn(serviceResponse);

        ResponseEntity<BaseResponse<ChangeMemberStatusResponse>> response =
                memberController.changeMemberStatus(TARGET_MEMBER_ID, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("회원 상태가 변경 성공");
        assertThat(response.getBody().data().memberId()).isEqualTo(TARGET_MEMBER_ID);
        assertThat(response.getBody().data().status()).isEqualTo("SUSPENDED");
    }

    @Test
    @DisplayName("회원 상태 변경 - 200 OK 및 변경된 상태 반환 (SUSPENDED → ACTIVE)")
    void changeMemberStatus_suspendedToActive_success() {
        ChangeMemberStatusRequest request = new ChangeMemberStatusRequest(MemberStatus.ACTIVE);
        ChangeMemberStatusResponse serviceResponse = new ChangeMemberStatusResponse(
                TARGET_MEMBER_ID,
                "ACTIVE",
                LocalDateTime.now()
        );
        given(memberService.changeMemberStatus(TARGET_MEMBER_ID, request)).willReturn(serviceResponse);

        ResponseEntity<BaseResponse<ChangeMemberStatusResponse>> response =
                memberController.changeMemberStatus(TARGET_MEMBER_ID, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("회원 상태 변경 - 존재하지 않는 회원이면 ServiceErrorException 전파")
    void changeMemberStatus_memberNotFound() {
        ChangeMemberStatusRequest request = new ChangeMemberStatusRequest(MemberStatus.SUSPENDED);
        given(memberService.changeMemberStatus(TARGET_MEMBER_ID, request))
                .willThrow(new ServiceErrorException(ERR_NOT_FOUND_MEMBER));

        assertThatThrownBy(() -> memberController.changeMemberStatus(TARGET_MEMBER_ID, request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 회원입니다");
    }

    @Test
    @DisplayName("회원 상태 변경 - 잘못된 상태 전환 시 ServiceErrorException 전파")
    void changeMemberStatus_invalidTransition() {
        ChangeMemberStatusRequest request = new ChangeMemberStatusRequest(MemberStatus.WITHDRAWAL);
        given(memberService.changeMemberStatus(TARGET_MEMBER_ID, request))
                .willThrow(new ServiceErrorException(ERR_INVALID_MEMBER_STATUS_TRANSITION));

        assertThatThrownBy(() -> memberController.changeMemberStatus(TARGET_MEMBER_ID, request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("잘못된 상태 전환입니다, ACTIVE와 SUSPENDED 간의 전환만 가능합니다");
    }

    @Test
    @DisplayName("회원 상태 변경 - 응답 updatedAt 필드가 null이 아님")
    void changeMemberStatus_responseContainsUpdatedAt() {
        LocalDateTime fixedTime = LocalDateTime.of(2024, 1, 15, 10, 0, 0);
        ChangeMemberStatusRequest request = new ChangeMemberStatusRequest(MemberStatus.SUSPENDED);
        ChangeMemberStatusResponse serviceResponse = new ChangeMemberStatusResponse(
                TARGET_MEMBER_ID,
                "SUSPENDED",
                fixedTime
        );
        given(memberService.changeMemberStatus(TARGET_MEMBER_ID, request)).willReturn(serviceResponse);

        ResponseEntity<BaseResponse<ChangeMemberStatusResponse>> response =
                memberController.changeMemberStatus(TARGET_MEMBER_ID, request);

        assertThat(response.getBody().data().updatedAt()).isEqualTo(fixedTime);
    }
    // endregion
}
