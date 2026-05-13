package four_tential.potential.presentation.review;

import four_tential.potential.application.review.ReviewService;
import four_tential.potential.common.dto.PageResponse;
import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.infra.security.principal.MemberPrincipal;
import four_tential.potential.presentation.review.dto.request.ReviewCreateRequest;
import four_tential.potential.presentation.review.dto.request.ReviewUpdateRequest;
import four_tential.potential.presentation.review.dto.response.ReviewSummaryResponse;
import four_tential.potential.presentation.review.dto.response.ReviewLikeResponse;
import four_tential.potential.presentation.review.dto.response.ReviewResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "후기", description = "후기 작성 · 조회 · 수정 · 삭제 · 좋아요 · AI 요약 API")
@Validated
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(
            summary = "후기 작성",
            description = """
                    수강 완료된 클래스에 후기를 작성합니다.
                    
                    - 결제 상태가 CONFIRMED인 주문 ID가 있어야 작성 가능합니다.
                    - 주문당 후기는 1개만 작성할 수 있습니다 (UNIQUE 제약).
                    - 이미지 URL은 S3 업로드 후 반환된 URL을 전달하세요 (최대 5장).
                    - 후기 저장 직후 @Async 비동기로 AI 요약이 갱신됩니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "후기 등록 완료"),
            @ApiResponse(responseCode = "400", description = "유효하지 않은 요청값 (rating 범위, content 공백 등)"),
            @ApiResponse(responseCode = "404", description = "클래스 또는 주문을 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "이미 후기를 작성한 주문")
    })
    @PostMapping("/courses/{courseId}/reviews")
    public ResponseEntity<BaseResponse<ReviewResponse>> create(
            @Parameter(description = "후기를 작성할 클래스 ID", required = true)
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

    @Operation(
            summary = "클래스별 후기 목록 조회",
            description = """
                    특정 클래스의 후기 목록을 페이지네이션으로 조회합니다.
                    
                    - 인증 없이 조회 가능합니다.
                    - 기본값: page=0, size=20
                    - 최신순으로 정렬됩니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "후기 목록 조회 완료"),
            @ApiResponse(responseCode = "400", description = "page < 0 또는 size < 1"),
            @ApiResponse(responseCode = "404", description = "클래스를 찾을 수 없음")
    })
    @GetMapping("/courses/{courseId}/reviews")
    public ResponseEntity<BaseResponse<PageResponse<ReviewResponse>>> findAllByCourse(
            @Parameter(description = "조회할 클래스 ID", required = true)
            @PathVariable UUID courseId,
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
            @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", example = "20")
            @Positive(message = "페이지 크기는 1 이상이어야 합니다") @RequestParam(defaultValue = "20") int size
    ) {
        PageResponse<ReviewResponse> response = reviewService.findAllByCourse(courseId, page, size);
        return ResponseEntity.ok(
                BaseResponse.success(HttpStatus.OK.name(), "후기 목록 조회가 완료되었습니다", response)
        );
    }

    @Operation(
            summary = "후기 단건 조회",
            description = """
                    후기 ID로 특정 후기를 조회합니다.
                    
                    - 인증 없이 조회 가능합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "후기 조회 완료"),
            @ApiResponse(responseCode = "404", description = "후기를 찾을 수 없음")
    })
    @GetMapping("/reviews/{reviewId}")
    public ResponseEntity<BaseResponse<ReviewResponse>> findById(
            @Parameter(description = "조회할 후기 ID", required = true)
            @PathVariable UUID reviewId
    ) {
        ReviewResponse response = reviewService.findById(reviewId);
        return ResponseEntity.ok(
                BaseResponse.success(HttpStatus.OK.name(), "후기 조회가 완료되었습니다", response)
        );
    }

    @Operation(
            summary = "후기 수정",
            description = """
                    본인이 작성한 후기를 수정합니다.
                    
                    - 작성자 본인만 수정 가능합니다.
                    - rating, content, imageUrls 모두 수정 가능합니다.
                    - 수정 후 @Async 비동기로 AI 요약이 재갱신됩니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "후기 수정 완료"),
            @ApiResponse(responseCode = "400", description = "유효하지 않은 요청값"),
            @ApiResponse(responseCode = "403", description = "본인이 작성한 후기가 아님"),
            @ApiResponse(responseCode = "404", description = "후기를 찾을 수 없음")
    })
    @PutMapping("/reviews/{reviewId}")
    public ResponseEntity<BaseResponse<ReviewResponse>> update(
            @Parameter(description = "수정할 후기 ID", required = true)
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

    @Operation(
            summary = "후기 삭제",
            description = """
                    본인이 작성한 후기를 삭제합니다.
                    
                    - 작성자 본인만 삭제 가능합니다.
                    - 후기에 첨부된 이미지(ReviewImage)도 함께 삭제됩니다.
                    - S3에 업로드된 원본 이미지는 별도로 관리됩니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "후기 삭제 완료"),
            @ApiResponse(responseCode = "403", description = "본인이 작성한 후기가 아님"),
            @ApiResponse(responseCode = "404", description = "후기를 찾을 수 없음")
    })
    @DeleteMapping("/reviews/{reviewId}")
    public ResponseEntity<BaseResponse<Void>> delete(
            @Parameter(description = "삭제할 후기 ID", required = true)
            @PathVariable UUID reviewId,
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        reviewService.delete(principal.memberId(), reviewId);
        return ResponseEntity.ok(
                BaseResponse.success(HttpStatus.OK.name(), "후기가 삭제되었습니다", null)
        );
    }

    @Operation(
            summary = "클래스 후기 AI 요약 조회",
            description = """
                    특정 클래스의 전체 후기를 AI가 요약한 결과를 조회합니다.
                    
                    - 인증 없이 조회 가능합니다.
                    - 요약은 후기 작성/수정 시 @Async로 실시간 갱신되며, 매일 새벽 3시 Spring Batch Map-Reduce로 전체 재요약됩니다.
                    - 후기가 없거나 아직 요약이 생성되지 않은 경우 null이 반환될 수 있습니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "후기 요약 조회 완료"),
            @ApiResponse(responseCode = "404", description = "클래스를 찾을 수 없음")
    })
    @GetMapping("/courses/{courseId}/reviews/summary")
    public ResponseEntity<BaseResponse<ReviewSummaryResponse>> getSummary(
            @Parameter(description = "요약을 조회할 클래스 ID", required = true)
            @PathVariable UUID courseId
    ) {
        ReviewSummaryResponse response = reviewService.getSummary(courseId);
        return ResponseEntity.ok(
                BaseResponse.success(HttpStatus.OK.name(), "후기 요약 조회 성공", response)
        );
    }

    @Operation(
            summary = "후기 좋아요 토글",
            description = """
                    후기에 좋아요를 등록하거나 취소합니다.
                    
                    - 좋아요가 없으면 등록, 이미 있으면 취소됩니다 (토글).
                    - 본인이 작성한 후기에도 좋아요를 누를 수 있습니다.
                    - 응답의 liked 필드로 현재 상태를 확인할 수 있습니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "좋아요 처리 완료 (liked: true=등록, false=취소)"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "404", description = "후기를 찾을 수 없음")
    })
    @PostMapping("/reviews/{reviewId}/likes")
    public ResponseEntity<BaseResponse<ReviewLikeResponse>> toggleLike(
            @Parameter(description = "좋아요를 토글할 후기 ID", required = true)
            @PathVariable UUID reviewId,
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        ReviewLikeResponse response = reviewService.toggleLike(principal.memberId(), reviewId);
        return ResponseEntity.ok(
                BaseResponse.success(HttpStatus.OK.name(), "좋아요가 처리되었습니다", response)
        );
    }
}