package four_tential.potential.presentation.member;

import four_tential.potential.application.course.CourseService;
import four_tential.potential.application.course.CourseWishlistService;
import four_tential.potential.application.member.MemberService;
import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.common.dto.PageResponse;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.infra.security.principal.MemberPrincipal;
import four_tential.potential.presentation.member.model.request.*;
import four_tential.potential.presentation.course.model.response.InstructorCourseListItem;
import four_tential.potential.presentation.member.model.response.ChangeMemberStatusResponse;
import four_tential.potential.presentation.member.model.response.FollowedInstructorItem;
import four_tential.potential.presentation.member.model.response.FollowResponse;
import four_tential.potential.presentation.member.model.response.InstructorProfileResponse;
import four_tential.potential.presentation.member.model.response.MyPageResponse;
import four_tential.potential.presentation.member.model.response.OnBoardResponse;
import four_tential.potential.presentation.member.model.response.UpdateMyPageResponse;
import four_tential.potential.presentation.member.model.response.WishlistCourseItem;
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
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.UUID;

import static four_tential.potential.common.exception.domain.MemberExceptionEnum.ERR_INVALID_AUTHORIZE;
import static four_tential.potential.common.exception.domain.MemberExceptionEnum.ERR_TOKEN_NULL;


@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final CourseWishlistService courseWishlistService;
    private final CourseService courseService;

    // TODO 다른 회원 도메인 API 작업후
    //  POST /members/me/profile-image/presigned-url 관련 버킷에 담는 과정이 필요함

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
