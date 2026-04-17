package four_tential.potential.presentation.instructor_member;

import four_tential.potential.application.instructor_member.InstructorMemberService;
import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.common.dto.PageResponse;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.member.fixture.InstructorMemberFixture;
import four_tential.potential.domain.member.instructor_member.InstructorMemberStatus;
import four_tential.potential.infra.security.principal.MemberPrincipal;
import four_tential.potential.presentation.instructor_member.model.request.ApplyInstructorRequest;
import four_tential.potential.presentation.instructor_member.model.request.InstructorAction;
import four_tential.potential.presentation.instructor_member.model.request.InstructorActionRequest;
import four_tential.potential.presentation.instructor_member.model.response.ApplyInstructorResponse;
import four_tential.potential.presentation.instructor_member.model.response.InstructorActionResponse;
import four_tential.potential.presentation.instructor_member.model.response.InstructorApplicationDetail;
import four_tential.potential.presentation.instructor_member.model.response.InstructorApplicationItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static four_tential.potential.common.exception.domain.CourseExceptionEnum.ERR_NOT_FOUND_CATEGORY;
import static four_tential.potential.common.exception.domain.MemberExceptionEnum.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class InstructorMemberControllerTest {

    @InjectMocks
    private InstructorMemberController instructorMemberController;

    @Mock
    private InstructorMemberService instructorMemberService;

    private static final UUID MEMBER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final MemberPrincipal PRINCIPAL = new MemberPrincipal(MEMBER_ID, "test@test.com", "ROLE_STUDENT");

    private static final ApplyInstructorRequest DEFAULT_REQUEST = new ApplyInstructorRequest(
            InstructorMemberFixture.DEFAULT_CATEGORY_CODE,
            InstructorMemberFixture.DEFAULT_CONTENT,
            InstructorMemberFixture.DEFAULT_IMAGE_URL
    );

    @Test
    @DisplayName("강사 신청 - STUDENT가 아닌 역할(ADMIN, INSTRUCTOR)이면 ServiceErrorException 발생")
    void applyInstructor_notStudent() {
        MemberPrincipal adminPrincipal = new MemberPrincipal(MEMBER_ID, "admin@test.com", "ROLE_ADMIN");

        assertThatThrownBy(() -> instructorMemberController.applyInstructor(DEFAULT_REQUEST, adminPrincipal))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("일반 회원 외엔 강사 신청을 할 수 없습니다");
    }

    @Test
    @DisplayName("강사 신청 - 201 CREATED 및 PENDING 상태 응답 반환")
    void applyInstructor_success() {
        ApplyInstructorResponse serviceResponse = new ApplyInstructorResponse(
                InstructorMemberStatus.PENDING,
                InstructorMemberFixture.DEFAULT_CATEGORY_CODE,
                null
        );
        given(instructorMemberService.applyInstructor(MEMBER_ID, DEFAULT_REQUEST)).willReturn(serviceResponse);

        ResponseEntity<BaseResponse<ApplyInstructorResponse>> response =
                instructorMemberController.applyInstructor(DEFAULT_REQUEST, PRINCIPAL);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().status()).isEqualTo(InstructorMemberStatus.PENDING);
        assertThat(response.getBody().data().categoryCode()).isEqualTo(InstructorMemberFixture.DEFAULT_CATEGORY_CODE);
    }

    @Test
    @DisplayName("강사 신청 - 이미 강사인 경우 ServiceErrorException 전파")
    void applyInstructor_alreadyInstructor() {
        given(instructorMemberService.applyInstructor(MEMBER_ID, DEFAULT_REQUEST))
                .willThrow(new ServiceErrorException(ERR_ALREADY_INSTRUCTOR));

        assertThatThrownBy(() -> instructorMemberController.applyInstructor(DEFAULT_REQUEST, PRINCIPAL))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("이미 강사로 등록된 회원입니다");
    }

    @Test
    @DisplayName("강사 신청 - PENDING 상태의 기존 신청이 있으면 ServiceErrorException 전파")
    void applyInstructor_alreadyPending() {
        given(instructorMemberService.applyInstructor(MEMBER_ID, DEFAULT_REQUEST))
                .willThrow(new ServiceErrorException(ERR_ALREADY_IN_PROGRESS_APPLICATION));

        assertThatThrownBy(() -> instructorMemberController.applyInstructor(DEFAULT_REQUEST, PRINCIPAL))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("이미 처리 중인 강사 신청이 있습니다");
    }

    @Test
    @DisplayName("강사 신청 - 존재하지 않는 카테고리 코드이면 ServiceErrorException 전파")
    void applyInstructor_invalidCategory() {
        given(instructorMemberService.applyInstructor(MEMBER_ID, DEFAULT_REQUEST))
                .willThrow(new ServiceErrorException(ERR_NOT_FOUND_CATEGORY));

        assertThatThrownBy(() -> instructorMemberController.applyInstructor(DEFAULT_REQUEST, PRINCIPAL))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 카테고리입니다");
    }

    @Test
    @DisplayName("강사 신청 - 존재하지 않는 회원이면 ServiceErrorException 전파")
    void applyInstructor_memberNotFound() {
        given(instructorMemberService.applyInstructor(MEMBER_ID, DEFAULT_REQUEST))
                .willThrow(new ServiceErrorException(ERR_NOT_FOUND_MEMBER));

        assertThatThrownBy(() -> instructorMemberController.applyInstructor(DEFAULT_REQUEST, PRINCIPAL))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 회원입니다");
    }

    @Test
    @DisplayName("강사 신청 목록 조회 - 200 OK 및 페이지 응답 반환")
    void getInstructorApplications_success() {
        PageResponse<InstructorApplicationItem> pageResponse = new PageResponse<>(
                List.of(new InstructorApplicationItem(
                        UUID.randomUUID(), "홍길동", "hong@test.com",
                        "FITNESS", "피트니스", InstructorMemberStatus.PENDING, null
                )),
                0, 1, 1, 10, true
        );
        given(instructorMemberService.getInstructorApplications(null, PageRequest.of(0, 10)))
                .willReturn(pageResponse);

        ResponseEntity<BaseResponse<PageResponse<InstructorApplicationItem>>> response =
                instructorMemberController.getInstructorApplications(0, 10, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().content()).hasSize(1);
        assertThat(response.getBody().data().totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("강사 신청 목록 조회 - PENDING 필터 적용 시 필터된 결과 반환")
    void getInstructorApplications_withStatusFilter() {
        PageResponse<InstructorApplicationItem> pageResponse = new PageResponse<>(
                List.of(), 0, 0, 0, 10, true
        );
        given(instructorMemberService.getInstructorApplications(InstructorMemberStatus.PENDING, PageRequest.of(0, 10)))
                .willReturn(pageResponse);

        ResponseEntity<BaseResponse<PageResponse<InstructorApplicationItem>>> response =
                instructorMemberController.getInstructorApplications(0, 10, InstructorMemberStatus.PENDING);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().content()).isEmpty();
    }

    // region processInstructorApplication
    @Test
    @DisplayName("강사 신청 승인 - 200 OK 및 APPROVED 상태 응답 반환")
    void processInstructorApplication_approve() {
        UUID memberId = UUID.randomUUID();
        InstructorActionRequest request = new InstructorActionRequest(InstructorAction.APPROVE, null);
        InstructorActionResponse serviceResponse = new InstructorActionResponse(
                memberId, InstructorMemberStatus.APPROVED, java.time.LocalDateTime.now()
        );
        given(instructorMemberService.processInstructorApplication(memberId, request)).willReturn(serviceResponse);

        ResponseEntity<BaseResponse<InstructorActionResponse>> response =
                instructorMemberController.processInstructorApplication(memberId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().status()).isEqualTo(InstructorMemberStatus.APPROVED);
    }

    @Test
    @DisplayName("강사 신청 반려 - 200 OK 및 REJECTED 상태 응답 반환")
    void processInstructorApplication_reject() {
        UUID memberId = UUID.randomUUID();
        InstructorActionRequest request = new InstructorActionRequest(
                InstructorAction.REJECT, InstructorMemberFixture.DEFAULT_REJECT_REASON
        );
        InstructorActionResponse serviceResponse = new InstructorActionResponse(
                memberId, InstructorMemberStatus.REJECTED, java.time.LocalDateTime.now()
        );
        given(instructorMemberService.processInstructorApplication(memberId, request)).willReturn(serviceResponse);

        ResponseEntity<BaseResponse<InstructorActionResponse>> response =
                instructorMemberController.processInstructorApplication(memberId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().status()).isEqualTo(InstructorMemberStatus.REJECTED);
    }

    @Test
    @DisplayName("강사 신청 처리 - 이미 처리된 신청이면 ServiceErrorException 전파")
    void processInstructorApplication_alreadyProcessed() {
        UUID memberId = UUID.randomUUID();
        InstructorActionRequest request = new InstructorActionRequest(InstructorAction.APPROVE, null);
        given(instructorMemberService.processInstructorApplication(memberId, request))
                .willThrow(new ServiceErrorException(ERR_ALREADY_PROCESSED_APPLICATION));

        assertThatThrownBy(() -> instructorMemberController.processInstructorApplication(memberId, request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("이미 처리된 강사 신청입니다");
    }

    @Test
    @DisplayName("강사 신청 처리 - 신청 내역 없으면 ServiceErrorException 전파")
    void processInstructorApplication_notFound() {
        UUID unknownId = UUID.randomUUID();
        InstructorActionRequest request = new InstructorActionRequest(InstructorAction.APPROVE, null);
        given(instructorMemberService.processInstructorApplication(unknownId, request))
                .willThrow(new ServiceErrorException(ERR_NOT_FOUND_INSTRUCTOR_APPLICATION));

        assertThatThrownBy(() -> instructorMemberController.processInstructorApplication(unknownId, request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("강사 신청 내역이 존재하지 않습니다");
    }
    // endregion

    // region getInstructorApplicationDetail
    @Test
    @DisplayName("강사 신청 상세 조회 - 200 OK 및 상세 정보 반환")
    void getInstructorApplicationDetail_success() {
        UUID memberId = UUID.randomUUID();
        InstructorApplicationDetail detail = new InstructorApplicationDetail(
                memberId, "홍길동", "hong@test.com", "010-1234-5678",
                "FITNESS", "피트니스",
                InstructorMemberFixture.DEFAULT_CONTENT,
                InstructorMemberFixture.DEFAULT_IMAGE_URL,
                InstructorMemberStatus.PENDING, null, null, null
        );
        given(instructorMemberService.getInstructorApplicationDetail(memberId)).willReturn(detail);

        ResponseEntity<BaseResponse<InstructorApplicationDetail>> response =
                instructorMemberController.getInstructorApplicationDetail(memberId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().memberName()).isEqualTo("홍길동");
        assertThat(response.getBody().data().categoryCode()).isEqualTo("FITNESS");
        assertThat(response.getBody().data().rejectReason()).isNull();
    }

    @Test
    @DisplayName("강사 신청 상세 조회 - 신청 내역 없으면 ServiceErrorException 전파")
    void getInstructorApplicationDetail_notFound() {
        UUID unknownId = UUID.randomUUID();
        given(instructorMemberService.getInstructorApplicationDetail(unknownId))
                .willThrow(new ServiceErrorException(ERR_NOT_FOUND_INSTRUCTOR_APPLICATION));

        assertThatThrownBy(() -> instructorMemberController.getInstructorApplicationDetail(unknownId))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("강사 신청 내역이 존재하지 않습니다");
    }
    // endregion
}
