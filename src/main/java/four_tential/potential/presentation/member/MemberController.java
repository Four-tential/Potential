package four_tential.potential.presentation.member;

import four_tential.potential.application.member.MemberService;
import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.infra.security.principal.MemberPrincipal;
import four_tential.potential.presentation.member.model.request.ChangePasswordRequest;
import four_tential.potential.presentation.member.model.request.OnBoardRequest;
import four_tential.potential.presentation.member.model.request.UpdateMyPageRequest;
import four_tential.potential.presentation.member.model.request.UpdateOnBoardRequest;
import four_tential.potential.presentation.member.model.response.MyPageResponse;
import four_tential.potential.presentation.member.model.response.OnBoardResponse;
import four_tential.potential.presentation.member.model.response.UpdateMyPageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/v1/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    // TODO 다른 회원 도메인 API 작업후
    //  POST /members/me/profile-image/presigned-url 관련 버킷에 담는 과정이 필요함

    @GetMapping("/me")
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

    @PatchMapping("/me")
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

    @PostMapping("/me/onboarding")
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

    @PatchMapping("/me/password")
    public ResponseEntity<BaseResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        memberService.changePassword(principal.memberId(), request);
        return ResponseEntity.status(HttpStatus.OK)
                .body(BaseResponse.success(
                        HttpStatus.OK.name(),
                        "비밀번호가 변경 성공",
                        null
                ));
    }

    @PatchMapping("/me/onboarding")
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
}
