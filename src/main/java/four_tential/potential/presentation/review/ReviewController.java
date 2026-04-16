package four_tential.potential.presentation.review;

import four_tential.potential.application.review.ReviewService;
import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.infra.security.principal.MemberPrincipal;
import four_tential.potential.presentation.review.dto.request.ReviewCreateRequest;
import four_tential.potential.presentation.review.dto.request.ReviewUpdateRequest;
import four_tential.potential.presentation.review.dto.response.ReviewResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    // 후기 작성
    @PostMapping("/courses/{courseId}/reviews")
    public ResponseEntity<BaseResponse<ReviewResponse>> create(
            @PathVariable UUID courseId,
            @RequestBody @Valid ReviewCreateRequest request,
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        ReviewResponse response = reviewService.create(
                principal.memberId(),
                courseId,
                request.getOrderId(),
                request.getRating(),
                request.getContent(),
                request.getImageUrls()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success(HttpStatus.CREATED.name(), "후기가 등록되었습니다", response));
    }

    // 코스별 후기 목록 조회
    @GetMapping("/courses/{courseId}/reviews")
    public ResponseEntity<BaseResponse<List<ReviewResponse>>> findAllByCourse(
            @PathVariable UUID courseId
    ) {
        List<ReviewResponse> response = reviewService.findAllByCourse(courseId);
        return ResponseEntity.ok(
                BaseResponse.success(HttpStatus.OK.name(), "후기 목록 조회가 완료되었습니다", response)
        );
    }

    // 후기 단건 조회
    @GetMapping("/reviews/{reviewId}")
    public ResponseEntity<BaseResponse<ReviewResponse>> findById(
            @PathVariable UUID reviewId
    ) {
        ReviewResponse response = reviewService.findById(reviewId);
        return ResponseEntity.ok(
                BaseResponse.success(HttpStatus.OK.name(), "후기 조회가 완료되었습니다", response)
        );
    }

    // 후기 수정
    @PutMapping("/reviews/{reviewId}")
    public ResponseEntity<BaseResponse<ReviewResponse>> update(
            @PathVariable UUID reviewId,
            @RequestBody @Valid ReviewUpdateRequest request,
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        ReviewResponse response = reviewService.update(
                principal.memberId(),
                reviewId,
                request.getRating(),
                request.getContent(),
                request.getImageUrls()
        );
        return ResponseEntity.ok(
                BaseResponse.success(HttpStatus.OK.name(), "후기가 수정되었습니다", response)
        );
    }

    // 후기 삭제
    @DeleteMapping("/reviews/{reviewId}")
    public ResponseEntity<BaseResponse<Void>> delete(
            @PathVariable UUID reviewId,
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        reviewService.delete(principal.memberId(), reviewId);
        return ResponseEntity.ok(
                BaseResponse.success(HttpStatus.OK.name(), "후기가 삭제되었습니다", null)
        );
    }
}