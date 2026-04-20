package four_tential.potential.presentation.member;

import four_tential.potential.application.course.CourseService;
import four_tential.potential.application.course.CourseWishlistService;
import four_tential.potential.application.member.MemberService;
import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.common.dto.PageResponse;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.attendance.AttendanceStatus;
import four_tential.potential.domain.course.course.CourseLevel;
import four_tential.potential.domain.course.course.CourseStatus;
import four_tential.potential.presentation.course.model.response.CourseStudentItem;
import four_tential.potential.presentation.course.model.response.InstructorCourseListItem;
import four_tential.potential.domain.member.fixture.MemberFixture;
import four_tential.potential.domain.member.member_onboard.MemberOnBoardGoal;
import four_tential.potential.infra.security.principal.MemberPrincipal;
import four_tential.potential.presentation.member.model.request.ChangePasswordRequest;
import four_tential.potential.presentation.member.model.request.OnBoardRequest;
import four_tential.potential.presentation.member.model.request.UpdateMyPageRequest;
import four_tential.potential.presentation.member.model.request.UpdateOnBoardRequest;
import four_tential.potential.presentation.member.model.request.WithdrawalRequest;
import four_tential.potential.presentation.member.model.response.FollowedInstructorItem;
import four_tential.potential.presentation.member.model.response.FollowResponse;
import four_tential.potential.presentation.member.model.response.InstructorProfileResponse;
import four_tential.potential.presentation.member.model.response.WishlistCourseItem;
import four_tential.potential.presentation.member.model.response.MyPageResponse;
import four_tential.potential.presentation.member.model.response.OnBoardResponse;
import four_tential.potential.presentation.member.model.response.UpdateMyPageResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static four_tential.potential.common.exception.domain.CourseExceptionEnum.ERR_COURSE_IN_PREPARATION;
import static four_tential.potential.common.exception.domain.CourseExceptionEnum.ERR_FORBIDDEN_COURSE;
import static four_tential.potential.common.exception.domain.CourseExceptionEnum.ERR_NOT_FOUND_CATEGORY;
import static four_tential.potential.common.exception.domain.CourseExceptionEnum.ERR_NOT_FOUND_COURSE;
import static four_tential.potential.common.exception.domain.MemberExceptionEnum.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MemberControllerTest {

    @Mock
    private MemberService memberService;

    @Mock
    private CourseWishlistService courseWishlistService;

    @Mock
    private CourseService courseService;

    @Mock
    private HttpServletResponse httpServletResponse;

    @InjectMocks
    private MemberController memberController;

    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final MemberPrincipal PRINCIPAL = new MemberPrincipal(MEMBER_ID, MemberFixture.DEFAULT_EMAIL, "ROLE_STUDENT");
    private static final String AUTHORIZATION = "Bearer valid.access.token";
    private static final String ACCESS_TOKEN = "valid.access.token";

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

    @Test
    @DisplayName("비밀번호 변경 - 200 OK, data는 null, 성공 메시지 반환")
    void changePassword_success() {
        ChangePasswordRequest request = new ChangePasswordRequest("OldP@ss1!", "NewP@ss2!");
        doNothing().when(memberService).changePassword(MEMBER_ID, request);

        ResponseEntity<BaseResponse<Void>> response = memberController.changePassword(request, PRINCIPAL);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("비밀번호 변경 성공");
        assertThat(response.getBody().data()).isNull();
    }

    @Test
    @DisplayName("비밀번호 변경 - 존재하지 않는 회원이면 ServiceErrorException 전파")
    void changePassword_memberNotFound() {
        ChangePasswordRequest request = new ChangePasswordRequest("OldP@ss1!", "NewP@ss2!");
        doThrow(new ServiceErrorException(ERR_NOT_FOUND_MEMBER))
                .when(memberService).changePassword(MEMBER_ID, request);

        assertThatThrownBy(() -> memberController.changePassword(request, PRINCIPAL))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 회원입니다");
    }

    @Test
    @DisplayName("비밀번호 변경 - 현재 비밀번호 불일치 시 ServiceErrorException 전파")
    void changePassword_wrongCurrentPassword() {
        ChangePasswordRequest request = new ChangePasswordRequest("WrongP@ss!", "NewP@ss2!");
        doThrow(new ServiceErrorException(ERR_WRONG_CURRENT_PASSWORD))
                .when(memberService).changePassword(MEMBER_ID, request);

        assertThatThrownBy(() -> memberController.changePassword(request, PRINCIPAL))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("현재 비밀번호가 올바르지 않습니다");
    }

    @Test
    @DisplayName("비밀번호 변경 - 현재와 동일한 비밀번호로 변경 시 ServiceErrorException 전파")
    void changePassword_sameAsCurrentPassword() {
        ChangePasswordRequest request = new ChangePasswordRequest("P@ssw0rd1!", "P@ssw0rd1!");
        doThrow(new ServiceErrorException(ERR_SAME_AS_CURRENT_PASSWORD))
                .when(memberService).changePassword(MEMBER_ID, request);

        assertThatThrownBy(() -> memberController.changePassword(request, PRINCIPAL))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("현재 비밀번호와 동일한 비밀번호로 변경할 수 없습니다");
    }

    @Test
    @DisplayName("회원 탈퇴 - 200 OK, data는 null, 성공 메시지 반환")
    void withdraw_success() {
        WithdrawalRequest request = new WithdrawalRequest("P@ssw0rd1!");
        doNothing().when(memberService).withdrawMember(MEMBER_ID, MemberFixture.DEFAULT_EMAIL, ACCESS_TOKEN, request);

        ResponseEntity<BaseResponse<Void>> response = memberController.withdraw(AUTHORIZATION, request, PRINCIPAL, httpServletResponse);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("회원 탈퇴 성공");
        assertThat(response.getBody().data()).isNull();
        verify(httpServletResponse).addHeader(eq(HttpHeaders.SET_COOKIE), contains("refreshToken="));
        verify(httpServletResponse).addHeader(eq(HttpHeaders.SET_COOKIE), contains("Max-Age=0"));
    }

    @Test
    @DisplayName("회원 탈퇴 - Authorization 헤더가 없으면 ServiceErrorException 전파")
    void withdraw_missingAuthorization() {
        WithdrawalRequest request = new WithdrawalRequest("P@ssw0rd1!");

        assertThatThrownBy(() -> memberController.withdraw(null, request, PRINCIPAL, httpServletResponse))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("인증 정보가 비어있습니다");

        verify(memberService, never()).withdrawMember(any(), any(), any(), any());
        verify(httpServletResponse, never()).addHeader(any(), any());
    }

    @Test
    @DisplayName("회원 탈퇴 - Bearer 형식이 아니면 ServiceErrorException 전파")
    void withdraw_invalidAuthorizationPrefix() {
        WithdrawalRequest request = new WithdrawalRequest("P@ssw0rd1!");

        assertThatThrownBy(() -> memberController.withdraw("Token invalid.access.token", request, PRINCIPAL, httpServletResponse))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("인증 정보가 비어있습니다");

        verify(memberService, never()).withdrawMember(any(), any(), any(), any());
        verify(httpServletResponse, never()).addHeader(any(), any());
    }

    @Test
    @DisplayName("회원 탈퇴 - Bearer 뒤 토큰이 비어 있으면 ServiceErrorException 전파")
    void withdraw_blankAccessToken() {
        WithdrawalRequest request = new WithdrawalRequest("P@ssw0rd1!");

        assertThatThrownBy(() -> memberController.withdraw("Bearer   ", request, PRINCIPAL, httpServletResponse))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("잘못된 인증 정보입니다, 다시 로그인 하시기 바랍니다");

        verify(memberService, never()).withdrawMember(any(), any(), any(), any());
        verify(httpServletResponse, never()).addHeader(any(), any());
    }

    @Test
    @DisplayName("회원 탈퇴 - 비밀번호 불일치 시 ServiceErrorException 전파")
    void withdraw_wrongPassword() {
        WithdrawalRequest request = new WithdrawalRequest("WrongPass!");
        doThrow(new ServiceErrorException(ERR_WRONG_PASSWORD))
                .when(memberService).withdrawMember(MEMBER_ID, MemberFixture.DEFAULT_EMAIL, ACCESS_TOKEN, request);

        assertThatThrownBy(() -> memberController.withdraw(AUTHORIZATION, request, PRINCIPAL, httpServletResponse))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("비밀번호가 올바르지 않습니다");
    }

    @Test
    @DisplayName("회원 탈퇴 - 수강 예정 코스 존재 시 ServiceErrorException 전파")
    void withdraw_hasScheduledCourse() {
        WithdrawalRequest request = new WithdrawalRequest("P@ssw0rd1!");
        doThrow(new ServiceErrorException(ERR_HAS_COURSE))
                .when(memberService).withdrawMember(MEMBER_ID, MemberFixture.DEFAULT_EMAIL, ACCESS_TOKEN, request);

        assertThatThrownBy(() -> memberController.withdraw(AUTHORIZATION, request, PRINCIPAL, httpServletResponse))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("수강해야할 코스가 있어 탈퇴가 불가합니다");
    }

    @Test
    @DisplayName("회원 탈퇴 - 진행 중인 강사 코스 존재 시 ServiceErrorException 전파")
    void withdraw_hasActiveInstructorCourses() {
        WithdrawalRequest request = new WithdrawalRequest("P@ssw0rd1!");
        doThrow(new ServiceErrorException(ERR_HAS_ACTIVE_INSTRUCTOR_COURSES))
                .when(memberService).withdrawMember(MEMBER_ID, MemberFixture.DEFAULT_EMAIL, ACCESS_TOKEN, request);

        assertThatThrownBy(() -> memberController.withdraw(AUTHORIZATION, request, PRINCIPAL, httpServletResponse))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("진행 중인 코스가 있어 탈퇴가 불가합니다");
    }

    @Test
    @DisplayName("팔로우 - 201 CREATED, 팔로우 응답 반환")
    void followInstructor_success() {
        UUID instructorMemberId = UUID.randomUUID();
        FollowResponse serviceResponse = FollowResponse.register(instructorMemberId, true);
        given(memberService.followInstructor(MEMBER_ID, instructorMemberId)).willReturn(serviceResponse);

        ResponseEntity<BaseResponse<FollowResponse>> response =
                memberController.followInstructor(instructorMemberId, PRINCIPAL);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("팔로우 성공");
        assertThat(response.getBody().data().instructorId()).isEqualTo(instructorMemberId);
        assertThat(response.getBody().data().isFollowed()).isTrue();
    }

    @Test
    @DisplayName("팔로우 - 본인 팔로우 시 ServiceErrorException 전파")
    void followInstructor_selfFollow() {
        UUID instructorMemberId = UUID.randomUUID();
        given(memberService.followInstructor(MEMBER_ID, instructorMemberId))
                .willThrow(new ServiceErrorException(ERR_CANNOT_FOLLOW_SELF));

        assertThatThrownBy(() -> memberController.followInstructor(instructorMemberId, PRINCIPAL))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("본인을 팔로우 할 수 없습니다");
    }

    @Test
    @DisplayName("팔로우 - 존재하지 않는 강사면 ServiceErrorException 전파")
    void followInstructor_instructorNotFound() {
        UUID instructorMemberId = UUID.randomUUID();
        given(memberService.followInstructor(MEMBER_ID, instructorMemberId))
                .willThrow(new ServiceErrorException(ERR_NOT_FOUND_INSTRUCTOR));

        assertThatThrownBy(() -> memberController.followInstructor(instructorMemberId, PRINCIPAL))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 강사입니다");
    }

    @Test
    @DisplayName("팔로우 - 이미 팔로우한 강사면 ServiceErrorException 전파")
    void followInstructor_alreadyFollowed() {
        UUID instructorMemberId = UUID.randomUUID();
        given(memberService.followInstructor(MEMBER_ID, instructorMemberId))
                .willThrow(new ServiceErrorException(ERR_ALREADY_FOLLOWED));

        assertThatThrownBy(() -> memberController.followInstructor(instructorMemberId, PRINCIPAL))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("이미 팔로우한 강사입니다");
    }

    @Test
    @DisplayName("팔로우 해제 - 200 OK, isFollowed=false 응답 반환")
    void unfollowInstructor_success() {
        UUID instructorMemberId = UUID.randomUUID();
        FollowResponse serviceResponse = FollowResponse.register(instructorMemberId, false);
        given(memberService.unfollowInstructor(MEMBER_ID, instructorMemberId)).willReturn(serviceResponse);

        ResponseEntity<BaseResponse<FollowResponse>> response =
                memberController.unfollowInstructor(instructorMemberId, PRINCIPAL);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("팔로우 해제 성공");
        assertThat(response.getBody().data().isFollowed()).isFalse();
        assertThat(response.getBody().data().instructorId()).isEqualTo(instructorMemberId);
    }

    @Test
    @DisplayName("팔로우 해제 - 존재하지 않는 강사면 ServiceErrorException 전파")
    void unfollowInstructor_instructorNotFound() {
        UUID instructorMemberId = UUID.randomUUID();
        given(memberService.unfollowInstructor(MEMBER_ID, instructorMemberId))
                .willThrow(new ServiceErrorException(ERR_NOT_FOUND_INSTRUCTOR));

        assertThatThrownBy(() -> memberController.unfollowInstructor(instructorMemberId, PRINCIPAL))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 강사입니다");
    }

    @Test
    @DisplayName("팔로우 해제 - 팔로우 기록이 없으면 ServiceErrorException 전파")
    void unfollowInstructor_followNotFound() {
        UUID instructorMemberId = UUID.randomUUID();
        given(memberService.unfollowInstructor(MEMBER_ID, instructorMemberId))
                .willThrow(new ServiceErrorException(ERR_NOT_FOUND_FOLLOW));

        assertThatThrownBy(() -> memberController.unfollowInstructor(instructorMemberId, PRINCIPAL))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("팔로우한 기록이 없습니다");
    }

    @Test
    @DisplayName("팔로우 목록 조회 - 200 OK 및 페이지 응답 반환")
    void getMyFollows_success() {
        PageRequest pageRequest = PageRequest.of(0, 10);
        FollowedInstructorItem item = new FollowedInstructorItem(
                UUID.randomUUID(), "강사이름", "https://img.url/profile.png",
                "FITNESS", "피트니스", 3L, 4.5, LocalDateTime.now()
        );
        PageResponse<FollowedInstructorItem> serviceResponse =
                PageResponse.register(new PageImpl<>(List.of(item), pageRequest, 1));
        given(memberService.getMyFollows(MEMBER_ID, pageRequest)).willReturn(serviceResponse);

        ResponseEntity<BaseResponse<PageResponse<FollowedInstructorItem>>> response =
                memberController.getMyFollows(0, 10, PRINCIPAL);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("팔로우한 강사 목록 조회 성공");
        assertThat(response.getBody().data().content()).hasSize(1);
        assertThat(response.getBody().data().content().get(0).name()).isEqualTo("강사이름");
        assertThat(response.getBody().data().totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("팔로우 목록 조회 - 팔로우 없으면 빈 페이지 반환")
    void getMyFollows_empty() {
        PageRequest pageRequest = PageRequest.of(0, 10);
        PageResponse<FollowedInstructorItem> serviceResponse =
                PageResponse.register(new PageImpl<>(List.of(), pageRequest, 0));
        given(memberService.getMyFollows(MEMBER_ID, pageRequest)).willReturn(serviceResponse);

        ResponseEntity<BaseResponse<PageResponse<FollowedInstructorItem>>> response =
                memberController.getMyFollows(0, 10, PRINCIPAL);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().content()).isEmpty();
        assertThat(response.getBody().data().totalElements()).isZero();
    }

    @Test
    @DisplayName("팔로우 목록 조회 - page=1, size=5 파라미터가 서비스에 올바르게 전달됨")
    void getMyFollows_customPageParams() {
        PageRequest pageRequest = PageRequest.of(1, 5);
        PageResponse<FollowedInstructorItem> serviceResponse =
                PageResponse.register(new PageImpl<>(List.of(), pageRequest, 0));
        given(memberService.getMyFollows(MEMBER_ID, pageRequest)).willReturn(serviceResponse);

        ResponseEntity<BaseResponse<PageResponse<FollowedInstructorItem>>> response =
                memberController.getMyFollows(1, 5, PRINCIPAL);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().currentPage()).isEqualTo(1);
        assertThat(response.getBody().data().size()).isEqualTo(5);
    }

    @Test
    @DisplayName("강사 프로필 조회 - 200 OK 및 프로필 응답 반환")
    void getInstructorProfile_success() {
        UUID instructorId = UUID.randomUUID();
        InstructorProfileResponse serviceResponse = new InstructorProfileResponse(
                instructorId,
                "박지현",
                "https://cdn.example.com/profile.jpg",
                "FITNESS",
                "피트니스",
                "10년 경력의 필라테스 강사입니다.",
                12L,
                4.8,
                234L
        );
        given(memberService.getInstructorProfile(instructorId)).willReturn(serviceResponse);

        ResponseEntity<BaseResponse<InstructorProfileResponse>> response =
                memberController.getInstructorProfile(instructorId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("강사 프로필 조회 성공");
        assertThat(response.getBody().data().memberId()).isEqualTo(instructorId);
        assertThat(response.getBody().data().categoryCode()).isEqualTo("FITNESS");
        assertThat(response.getBody().data().courseCount()).isEqualTo(12L);
        assertThat(response.getBody().data().averageRating()).isEqualTo(4.8);
        assertThat(response.getBody().data().totalStudentCount()).isEqualTo(234L);
    }

    @Test
    @DisplayName("강사 프로필 조회 - 존재하지 않는 강사면 ServiceErrorException 전파")
    void getInstructorProfile_instructorNotFound() {
        UUID instructorId = UUID.randomUUID();
        given(memberService.getInstructorProfile(instructorId))
                .willThrow(new ServiceErrorException(ERR_NOT_FOUND_INSTRUCTOR));

        assertThatThrownBy(() -> memberController.getInstructorProfile(instructorId))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 강사입니다");
    }

    @Test
    @DisplayName("강사 프로필 조회 - 카테고리를 찾을 수 없으면 ServiceErrorException 전파")
    void getInstructorProfile_categoryNotFound() {
        UUID instructorId = UUID.randomUUID();
        given(memberService.getInstructorProfile(instructorId))
                .willThrow(new ServiceErrorException(ERR_NOT_FOUND_CATEGORY));

        assertThatThrownBy(() -> memberController.getInstructorProfile(instructorId))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 카테고리입니다");
    }


    @Test
    @DisplayName("찜 목록 조회 - 200 OK 및 페이지 응답 반환")
    void getMyWishlistCourses_success() {
        WishlistCourseItem item = new WishlistCourseItem(
                UUID.randomUUID(), "소도구 필라테스 입문반", "소강사",
                "https://example.com/thumb.jpg", "PILATES", "필라테스",
                BigInteger.valueOf(70000), CourseStatus.OPEN,
                LocalDateTime.now().plusDays(10), LocalDateTime.now()
        );
        PageRequest pageRequest = PageRequest.of(0, 10);
        PageResponse<WishlistCourseItem> serviceResponse =
                PageResponse.register(new PageImpl<>(List.of(item), pageRequest, 1));
        given(courseWishlistService.getMyWishlistCourses(MEMBER_ID, 0, 10)).willReturn(serviceResponse);

        ResponseEntity<BaseResponse<PageResponse<WishlistCourseItem>>> response =
                memberController.getMyWishlistCourses(0, 10, PRINCIPAL);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("찜 목록 조회 성공");
        assertThat(response.getBody().data().content()).hasSize(1);
        assertThat(response.getBody().data().content().get(0).title()).isEqualTo("소도구 필라테스 입문반");
        assertThat(response.getBody().data().totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("찜 목록 조회 - 찜한 코스 없으면 빈 페이지 반환")
    void getMyWishlistCourses_empty() {
        PageRequest pageRequest = PageRequest.of(0, 10);
        PageResponse<WishlistCourseItem> serviceResponse =
                PageResponse.register(new PageImpl<>(List.of(), pageRequest, 0));
        given(courseWishlistService.getMyWishlistCourses(MEMBER_ID, 0, 10)).willReturn(serviceResponse);

        ResponseEntity<BaseResponse<PageResponse<WishlistCourseItem>>> response =
                memberController.getMyWishlistCourses(0, 10, PRINCIPAL);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().content()).isEmpty();
        assertThat(response.getBody().data().totalElements()).isZero();
    }

    @Test
    @DisplayName("강사 본인 코스 목록 조회 - 200 OK 및 페이지 응답 반환 (PREPARATION 포함)")
    void getMyInstructorCourses_success() {
        UUID courseId = UUID.randomUUID();
        InstructorCourseListItem item = new InstructorCourseListItem(
                courseId, "소도구 필라테스", CourseLevel.BEGINNER, CourseStatus.PREPARATION,
                20, 5, BigInteger.valueOf(80000),
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(10)
        );
        PageRequest pageRequest = PageRequest.of(0, 10);
        PageResponse<InstructorCourseListItem> serviceResponse =
                PageResponse.register(new PageImpl<>(List.of(item), pageRequest, 1));
        given(courseService.getMyInstructorCourses(MEMBER_ID, pageRequest)).willReturn(serviceResponse);

        ResponseEntity<BaseResponse<PageResponse<InstructorCourseListItem>>> response =
                memberController.getMyInstructorCourses(PRINCIPAL, 0, 10);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("강사 본인 코스 목록 조회 성공");
        assertThat(response.getBody().data().content()).hasSize(1);
        assertThat(response.getBody().data().content().get(0).courseId()).isEqualTo(courseId);
        assertThat(response.getBody().data().content().get(0).status()).isEqualTo(CourseStatus.PREPARATION);
        assertThat(response.getBody().data().totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("강사 본인 코스 목록 조회 - 코스가 없으면 빈 페이지 반환")
    void getMyInstructorCourses_empty() {
        PageRequest pageRequest = PageRequest.of(0, 10);
        PageResponse<InstructorCourseListItem> serviceResponse =
                PageResponse.register(new PageImpl<>(List.of(), pageRequest, 0));
        given(courseService.getMyInstructorCourses(MEMBER_ID, pageRequest)).willReturn(serviceResponse);

        ResponseEntity<BaseResponse<PageResponse<InstructorCourseListItem>>> response =
                memberController.getMyInstructorCourses(PRINCIPAL, 0, 10);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().content()).isEmpty();
        assertThat(response.getBody().data().totalElements()).isZero();
    }

    @Test
    @DisplayName("강사 본인 코스 목록 조회 - page=1, size=5 파라미터가 서비스에 올바르게 전달됨")
    void getMyInstructorCourses_customPageParams() {
        PageRequest pageRequest = PageRequest.of(1, 5);
        PageResponse<InstructorCourseListItem> serviceResponse =
                PageResponse.register(new PageImpl<>(List.of(), pageRequest, 0));
        given(courseService.getMyInstructorCourses(MEMBER_ID, pageRequest)).willReturn(serviceResponse);

        ResponseEntity<BaseResponse<PageResponse<InstructorCourseListItem>>> response =
                memberController.getMyInstructorCourses(PRINCIPAL, 1, 5);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().currentPage()).isEqualTo(1);
        assertThat(response.getBody().data().size()).isEqualTo(5);
    }

    @Test
    @DisplayName("강사 본인 코스 목록 조회 - 승인된 강사 아니면 ServiceErrorException 전파")
    void getMyInstructorCourses_instructorNotFound() {
        PageRequest pageRequest = PageRequest.of(0, 10);
        given(courseService.getMyInstructorCourses(MEMBER_ID, pageRequest))
                .willThrow(new ServiceErrorException(ERR_NOT_FOUND_INSTRUCTOR));

        assertThatThrownBy(() -> memberController.getMyInstructorCourses(PRINCIPAL, 0, 10))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 강사입니다");
    }

    @Test
    @DisplayName("강사 코스 목록 조회 - 200 OK 및 페이지 응답 반환 (PREPARATION 제외)")
    void getInstructorCourses_success() {
        UUID instructorId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        InstructorCourseListItem item = new InstructorCourseListItem(
                courseId, "요가 중급반", CourseLevel.INTERMEDIATE, CourseStatus.OPEN,
                15, 10, BigInteger.valueOf(60000),
                LocalDateTime.now().plusDays(2), LocalDateTime.now().plusDays(12)
        );
        PageRequest pageRequest = PageRequest.of(0, 10);
        PageResponse<InstructorCourseListItem> serviceResponse =
                PageResponse.register(new PageImpl<>(List.of(item), pageRequest, 1));
        given(courseService.getInstructorCourses(instructorId, pageRequest)).willReturn(serviceResponse);

        ResponseEntity<BaseResponse<PageResponse<InstructorCourseListItem>>> response =
                memberController.getInstructorCourses(instructorId, 0, 10);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("강사 코스 목록 조회 성공");
        assertThat(response.getBody().data().content()).hasSize(1);
        assertThat(response.getBody().data().content().get(0).courseId()).isEqualTo(courseId);
        assertThat(response.getBody().data().content().get(0).status()).isEqualTo(CourseStatus.OPEN);
        assertThat(response.getBody().data().totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("강사 코스 목록 조회 - 코스가 없으면 빈 페이지 반환")
    void getInstructorCourses_empty() {
        UUID instructorId = UUID.randomUUID();
        PageRequest pageRequest = PageRequest.of(0, 10);
        PageResponse<InstructorCourseListItem> serviceResponse =
                PageResponse.register(new PageImpl<>(List.of(), pageRequest, 0));
        given(courseService.getInstructorCourses(instructorId, pageRequest)).willReturn(serviceResponse);

        ResponseEntity<BaseResponse<PageResponse<InstructorCourseListItem>>> response =
                memberController.getInstructorCourses(instructorId, 0, 10);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().content()).isEmpty();
        assertThat(response.getBody().data().totalElements()).isZero();
    }

    @Test
    @DisplayName("강사 코스 목록 조회 - page=1, size=5 파라미터가 서비스에 올바르게 전달됨")
    void getInstructorCourses_customPageParams() {
        UUID instructorId = UUID.randomUUID();
        PageRequest pageRequest = PageRequest.of(1, 5);
        PageResponse<InstructorCourseListItem> serviceResponse =
                PageResponse.register(new PageImpl<>(List.of(), pageRequest, 0));
        given(courseService.getInstructorCourses(instructorId, pageRequest)).willReturn(serviceResponse);

        ResponseEntity<BaseResponse<PageResponse<InstructorCourseListItem>>> response =
                memberController.getInstructorCourses(instructorId, 1, 5);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().currentPage()).isEqualTo(1);
        assertThat(response.getBody().data().size()).isEqualTo(5);
    }

    @Test
    @DisplayName("강사 코스 목록 조회 - 존재하지 않는 강사면 ServiceErrorException 전파")
    void getInstructorCourses_instructorNotFound() {
        UUID instructorId = UUID.randomUUID();
        given(courseService.getInstructorCourses(instructorId, PageRequest.of(0, 10)))
                .willThrow(new ServiceErrorException(ERR_NOT_FOUND_INSTRUCTOR));

        assertThatThrownBy(() -> memberController.getInstructorCourses(instructorId, 0, 10))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 강사입니다");
    }


    @Test
    @DisplayName("수강생 명단 조회 - 200 OK 및 페이지 응답 반환")
    void getCourseStudents_success() {
        UUID courseId = UUID.randomUUID();
        CourseStudentItem item = new CourseStudentItem(
                UUID.randomUUID(), "김수강",
                AttendanceStatus.ATTEND,
                LocalDateTime.of(2026, 1, 20, 14, 5)
        );
        PageRequest pageRequest = PageRequest.of(0, 10);
        PageResponse<CourseStudentItem> serviceResponse =
                PageResponse.register(new PageImpl<>(List.of(item), pageRequest, 1));
        given(courseService.getCourseStudents(courseId, MEMBER_ID, pageRequest)).willReturn(serviceResponse);

        ResponseEntity<BaseResponse<PageResponse<CourseStudentItem>>> response =
                memberController.getCourseStudents(courseId, PRINCIPAL, 0, 10);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("수강생 명단 조회 성공");
        assertThat(response.getBody().data().content()).hasSize(1);
        assertThat(response.getBody().data().content().get(0).memberName()).isEqualTo("김수강");
        assertThat(response.getBody().data().content().get(0).attendanceStatus()).isEqualTo(AttendanceStatus.ATTEND);
        assertThat(response.getBody().data().totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("수강생 명단 조회 - 수강생이 없으면 빈 페이지 반환")
    void getCourseStudents_empty() {
        UUID courseId = UUID.randomUUID();
        PageRequest pageRequest = PageRequest.of(0, 10);
        PageResponse<CourseStudentItem> serviceResponse =
                PageResponse.register(new PageImpl<>(List.of(), pageRequest, 0));
        given(courseService.getCourseStudents(courseId, MEMBER_ID, pageRequest)).willReturn(serviceResponse);

        ResponseEntity<BaseResponse<PageResponse<CourseStudentItem>>> response =
                memberController.getCourseStudents(courseId, PRINCIPAL, 0, 10);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().content()).isEmpty();
        assertThat(response.getBody().data().totalElements()).isZero();
    }

    @Test
    @DisplayName("수강생 명단 조회 - page=1, size=5 파라미터가 서비스에 올바르게 전달됨")
    void getCourseStudents_customPageParams() {
        UUID courseId = UUID.randomUUID();
        PageRequest pageRequest = PageRequest.of(1, 5);
        PageResponse<CourseStudentItem> serviceResponse =
                PageResponse.register(new PageImpl<>(List.of(), pageRequest, 0));
        given(courseService.getCourseStudents(courseId, MEMBER_ID, pageRequest)).willReturn(serviceResponse);

        ResponseEntity<BaseResponse<PageResponse<CourseStudentItem>>> response =
                memberController.getCourseStudents(courseId, PRINCIPAL, 1, 5);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().currentPage()).isEqualTo(1);
        assertThat(response.getBody().data().size()).isEqualTo(5);
    }

    @Test
    @DisplayName("수강생 명단 조회 - 코스가 없으면 ServiceErrorException 전파 (404)")
    void getCourseStudents_courseNotFound() {
        UUID courseId = UUID.randomUUID();
        given(courseService.getCourseStudents(courseId, MEMBER_ID, PageRequest.of(0, 10)))
                .willThrow(new ServiceErrorException(ERR_NOT_FOUND_COURSE));

        assertThatThrownBy(() -> memberController.getCourseStudents(courseId, PRINCIPAL, 0, 10))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 코스입니다");
    }

    @Test
    @DisplayName("수강생 명단 조회 - 본인 코스가 아니면 ServiceErrorException 전파 (403)")
    void getCourseStudents_forbiddenCourse() {
        UUID courseId = UUID.randomUUID();
        given(courseService.getCourseStudents(courseId, MEMBER_ID, PageRequest.of(0, 10)))
                .willThrow(new ServiceErrorException(ERR_FORBIDDEN_COURSE));

        assertThatThrownBy(() -> memberController.getCourseStudents(courseId, PRINCIPAL, 0, 10))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("본인 코스에 대해서만 조회할 수 있습니다");
    }

    @Test
    @DisplayName("수강생 명단 조회 - 승인된 강사가 아니면 ServiceErrorException 전파")
    void getCourseStudents_instructorNotFound() {
        UUID courseId = UUID.randomUUID();
        given(courseService.getCourseStudents(courseId, MEMBER_ID, PageRequest.of(0, 10)))
                .willThrow(new ServiceErrorException(ERR_NOT_FOUND_INSTRUCTOR));

        assertThatThrownBy(() -> memberController.getCourseStudents(courseId, PRINCIPAL, 0, 10))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 강사입니다");
    }

    @Test
    @DisplayName("수강생 명단 조회 - PREPARATION 코스면 ServiceErrorException 전파 (400)")
    void getCourseStudents_courseInPreparation() {
        UUID courseId = UUID.randomUUID();
        given(courseService.getCourseStudents(courseId, MEMBER_ID, PageRequest.of(0, 10)))
                .willThrow(new ServiceErrorException(ERR_COURSE_IN_PREPARATION));

        assertThatThrownBy(() -> memberController.getCourseStudents(courseId, PRINCIPAL, 0, 10))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("준비 중인 코스는 수강생을 조회할 수 없습니다");
    }
}
