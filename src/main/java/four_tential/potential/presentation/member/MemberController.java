package four_tential.potential.presentation.member;

import four_tential.potential.application.course.CourseService;
import four_tential.potential.application.course.CourseWishlistService;
import four_tential.potential.application.member.MemberService;
import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.common.dto.PageResponse;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.infra.security.principal.MemberPrincipal;
import four_tential.potential.presentation.member.model.request.ChangePasswordRequest;
import four_tential.potential.presentation.member.model.request.ChangeMemberStatusRequest;
import four_tential.potential.presentation.member.model.request.OnBoardRequest;
import four_tential.potential.presentation.member.model.request.UpdateMyPageRequest;
import four_tential.potential.presentation.member.model.request.UpdateOnBoardRequest;
import four_tential.potential.presentation.member.model.request.WithdrawalRequest;
import four_tential.potential.presentation.course.model.response.CourseStudentItem;
import four_tential.potential.presentation.course.model.response.InstructorCourseListItem;
import four_tential.potential.presentation.member.model.response.ChangeMemberStatusResponse;
import four_tential.potential.presentation.member.model.response.FollowedInstructorItem;
import four_tential.potential.presentation.member.model.response.FollowResponse;
import four_tential.potential.presentation.member.model.response.InstructorProfileResponse;
import four_tential.potential.presentation.member.model.response.MyPageResponse;
import four_tential.potential.presentation.member.model.response.OnBoardResponse;
import four_tential.potential.presentation.member.model.response.UpdateMyPageResponse;
import four_tential.potential.presentation.member.model.response.WishlistCourseItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.UUID;

import static four_tential.potential.common.exception.domain.MemberExceptionEnum.ERR_INVALID_AUTHORIZE;
import static four_tential.potential.common.exception.domain.MemberExceptionEnum.ERR_TOKEN_NULL;


@Tag(name = "회원", description = "회원 마이페이지·온보딩·비밀번호·팔로우·강사 프로필·관리자 API")
@Validated
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final CourseWishlistService courseWishlistService;
    private final CourseService courseService;

    @Operation(summary = "마이페이지 조회", description = "로그인한 회원의 마이페이지 정보를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping("/members/me")
    public ResponseEntity<BaseResponse<MyPageResponse>> getMyPageInfo(
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(BaseResponse.success(
                        HttpStatus.OK.name(),
                        "마이페이지 정보 조회 성공",
                        memberService.getMyPageInfo(principal.memberId())
                ));
    }

    @Operation(summary = "마이페이지 수정", description = "전화번호·프로필 이미지 URL을 수정합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "400", description = "유효성 검사 실패"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @PatchMapping("/members/me")
    public ResponseEntity<BaseResponse<UpdateMyPageResponse>> updateMyPageInfo(
            @Valid @RequestBody UpdateMyPageRequest request,
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(BaseResponse.success(
                        HttpStatus.OK.name(),
                        "마이페이지 정보 수정 성공",
                        memberService.updateMyPageInfo(principal.memberId(), request)
                ));
    }

    @Operation(summary = "온보딩 등록", description = "회원의 온보딩 정보를 등록합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 성공"),
            @ApiResponse(responseCode = "400", description = "유효성 검사 실패"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @PostMapping("/members/me/onboarding")
    public ResponseEntity<BaseResponse<OnBoardResponse>> registerOnBoarding(
            @Valid @RequestBody OnBoardRequest request,
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success(
                        HttpStatus.CREATED.name(),
                        "온보딩 등록 성공",
                        memberService.createOnBoarding(principal.memberId(), request)
                ));
    }

    @Operation(summary = "온보딩 수정", description = "회원의 온보딩 정보를 수정합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @PatchMapping("/members/me/onboarding")
    public ResponseEntity<BaseResponse<OnBoardResponse>> updateOnBoarding(
            @RequestBody UpdateOnBoardRequest request,
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(BaseResponse.success(
                        HttpStatus.OK.name(),
                        "온보딩 등록 변경 성공",
                        memberService.updateOnBoarding(principal.memberId(), request)
                ));
    }

    @Operation(summary = "비밀번호 변경", description = "현재 비밀번호를 확인 후 새 비밀번호로 변경합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "변경 성공"),
            @ApiResponse(responseCode = "400", description = "현재 비밀번호 불일치 / 유효성 검사 실패"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @PatchMapping("/members/me/password")
    public ResponseEntity<BaseResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        memberService.changePassword(principal.memberId(), request);
        return ResponseEntity.status(HttpStatus.OK)
                .body(BaseResponse.success(
                        HttpStatus.OK.name(),
                        "비밀번호 변경 성공",
                        null
                ));
    }

    @Operation(summary = "회원 탈퇴", description = "비밀번호를 확인 후 회원 탈퇴를 처리합니다. 리프레시 토큰 쿠키가 만료됩니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "탈퇴 성공"),
            @ApiResponse(responseCode = "400", description = "비밀번호 불일치"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @DeleteMapping("/members/me/withdrawals")
    public ResponseEntity<BaseResponse<Void>> withdraw(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody WithdrawalRequest request,
            @AuthenticationPrincipal MemberPrincipal principal,
            HttpServletResponse response
    ) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ServiceErrorException(ERR_TOKEN_NULL);
        }

        String accessToken = authorization.substring("Bearer ".length()).trim();
        if (accessToken.isEmpty()) {
            throw new ServiceErrorException(ERR_INVALID_AUTHORIZE);
        }

        memberService.withdrawMember(principal.memberId(), principal.email(), accessToken, request);
        response.addHeader(HttpHeaders.SET_COOKIE, expireRefreshTokenCookie().toString());

        return ResponseEntity.status(HttpStatus.OK)
                .body(BaseResponse.success(
                        HttpStatus.OK.name(),
                        "회원 탈퇴 성공",
                        null
                ));
    }

    private ResponseCookie expireRefreshTokenCookie() {
        return ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .sameSite("Strict")
                .path("/v1/auth")
                .maxAge(Duration.ZERO)
                .build();
    }

    @Operation(summary = "찜 목록 조회", description = "로그인한 회원의 찜한 코스 목록을 페이지로 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping("/members/me/wishlist-courses")
    public ResponseEntity<BaseResponse<PageResponse<WishlistCourseItem>>> getMyWishlistCourses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(BaseResponse.success(
                        HttpStatus.OK.name(),
                        "찜 목록 조회 성공",
                        courseWishlistService.getMyWishlistCourses(principal.memberId(), page, size)
                ));
    }

    @Operation(summary = "팔로우한 강사 목록 조회", description = "로그인한 회원이 팔로우한 강사 목록을 페이지로 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping("/members/me/follows")
    public ResponseEntity<BaseResponse<PageResponse<FollowedInstructorItem>>> getMyFollows(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal MemberPrincipal principal
            ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(BaseResponse.success(
                        HttpStatus.OK.name(),
                        "팔로우한 강사 목록 조회 성공",
                        memberService.getMyFollows(principal.memberId(), PageRequest.of(page, size))
                ));
    }

    @Operation(summary = "강사 공개 프로필 조회", description = "강사의 공개 프로필 정보를 조회합니다. 비인증 사용자도 조회 가능합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "강사 없음")
    })
    @GetMapping("/instructors/{instructorId}")
    public ResponseEntity<BaseResponse<InstructorProfileResponse>> getInstructorProfile(
            @PathVariable UUID instructorId
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(BaseResponse.success(
                        HttpStatus.OK.name(),
                        "강사 프로필 조회 성공",
                        memberService.getInstructorProfile(instructorId)
                ));
    }

    @Operation(summary = "수강생 명단 조회", description = "강사 본인 코스의 수강생 명단을 페이지로 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "403", description = "본인 코스 아님 / 강사 권한 필요"),
            @ApiResponse(responseCode = "404", description = "코스 없음")
    })
    @GetMapping("/instructors/me/courses/{courseId}/students")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<BaseResponse<PageResponse<CourseStudentItem>>> getCourseStudents(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(BaseResponse.success(
                        HttpStatus.OK.name(),
                        "수강생 명단 조회 성공",
                        courseService.getCourseStudents(courseId, principal.memberId(), PageRequest.of(page, size))
                ));
    }

    @Operation(summary = "강사 본인 코스 목록 조회", description = "강사 본인의 전체 코스 목록을 페이지로 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "403", description = "강사 권한 필요")
    })
    @GetMapping("/instructors/me/courses")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<BaseResponse<PageResponse<InstructorCourseListItem>>> getMyInstructorCourses(
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(BaseResponse.success(
                        HttpStatus.OK.name(),
                        "강사 본인 코스 목록 조회 성공",
                        courseService.getMyInstructorCourses(principal.memberId(), PageRequest.of(page, size))
                ));
    }

    @Operation(summary = "강사 코스 목록 조회", description = "특정 강사의 코스 목록을 페이지로 조회합니다. 비인증 사용자도 조회 가능합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "강사 없음")
    })
    @GetMapping("/instructors/{instructorId}/courses")
    public ResponseEntity<BaseResponse<PageResponse<InstructorCourseListItem>>> getInstructorCourses(
            @PathVariable UUID instructorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(BaseResponse.success(
                        HttpStatus.OK.name(),
                        "강사 코스 목록 조회 성공",
                        courseService.getInstructorCourses(instructorId, PageRequest.of(page, size))
                ));
    }

    @Operation(summary = "강사 팔로우 해제", description = "팔로우한 강사를 팔로우 해제합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "해제 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "404", description = "팔로우 기록 없음")
    })
    @DeleteMapping("/instructors/{memberId}/follows")
    public ResponseEntity<BaseResponse<FollowResponse>> unfollowInstructor(
            @PathVariable UUID memberId,
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(BaseResponse.success(
                        HttpStatus.OK.name(),
                        "팔로우 해제 성공",
                        memberService.unfollowInstructor(principal.memberId(), memberId)
                ));
    }

    @Operation(summary = "강사 팔로우", description = "강사를 팔로우합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "팔로우 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "404", description = "강사 없음"),
            @ApiResponse(responseCode = "409", description = "이미 팔로우한 강사")
    })
    @PostMapping("/instructors/{memberId}/follows")
    public ResponseEntity<BaseResponse<FollowResponse>> followInstructor(
            @PathVariable UUID memberId,
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success(
                        HttpStatus.CREATED.name(),
                        "팔로우 성공",
                        memberService.followInstructor(principal.memberId(), memberId)
                ));
    }

    @Operation(summary = "회원 상태 변경 (어드민)", description = "관리자가 특정 회원의 상태를 변경합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "변경 성공"),
            @ApiResponse(responseCode = "403", description = "어드민 권한 필요"),
            @ApiResponse(responseCode = "404", description = "회원 없음")
    })
    @PatchMapping("/admin/members/{memberId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BaseResponse<ChangeMemberStatusResponse>> changeMemberStatus(
            @PathVariable UUID memberId,
            @Valid @RequestBody ChangeMemberStatusRequest request
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(BaseResponse.success(
                        HttpStatus.OK.name(),
                        "회원 상태 변경 성공",
                        memberService.changeMemberStatus(memberId, request)
                ));
    }
}
