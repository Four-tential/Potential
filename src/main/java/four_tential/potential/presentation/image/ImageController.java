package four_tential.potential.presentation.image;

import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.infra.s3.ImageType;
import four_tential.potential.infra.s3.PresignedUrlResult;
import four_tential.potential.infra.s3.S3Service;
import four_tential.potential.infra.security.principal.MemberPrincipal;
import four_tential.potential.presentation.image.model.request.PresignedUrlRequest;
import four_tential.potential.presentation.image.model.response.PresignedUrlResponse;
import four_tential.potential.presentation.image.model.response.PresignedUrlResponse.PresignedUrlItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static four_tential.potential.common.exception.domain.MemberExceptionEnum.ERR_INVALID_AUTHORIZE;

@Tag(name = "이미지", description = "S3 Presigned URL 발급 API")
@RestController
@RequestMapping("/v1/images")
@RequiredArgsConstructor
public class ImageController {

    private final S3Service s3Service;

    @Operation(summary = "Presigned URL 발급", description = "S3에 직접 업로드하기 위한 Presigned PUT URL을 발급합니다. PROFILE·INSTRUCTOR 타입은 인증이 필요하며 memberId가 경로에 자동 포함됩니다. COURSE 타입은 courseId를 resourceId로 전달해야 합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "발급 성공"),
            @ApiResponse(responseCode = "400", description = "지원하지 않는 Content-Type / 유효성 검사 실패"),
            @ApiResponse(responseCode = "401", description = "인증 필요 (PROFILE·INSTRUCTOR 타입)")
    })
    @PostMapping("/presigned-urls")
    public ResponseEntity<BaseResponse<PresignedUrlResponse>> getPresignedUrls(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody PresignedUrlRequest request
    ) {
        UUID resourceId = switch (request.type()) {
            case PROFILE, INSTRUCTOR -> {
                if (principal == null) {
                    throw new ServiceErrorException(ERR_INVALID_AUTHORIZE);
                }
                yield principal.memberId();
            }
            case COURSE -> {
                if (request.resourceId() == null) {
                    throw new IllegalArgumentException("COURSE 타입은 resourceId가 필수입니다");
                }
                yield request.resourceId();
            }
            case REVIEW -> {
                if (request.resourceId() == null) {
                    throw new IllegalArgumentException("REVIEW 타입은 resourceId가 필수입니다");
                }
                yield request.resourceId();
            }
        };

        List<PresignedUrlResult> results = s3Service.generatePresignedUrls(
                request.type().getPrefix(), resourceId, request.contentTypes()
        );

        List<PresignedUrlItem> items = results.stream()
                .map(r -> new PresignedUrlItem(r.presignedUrl(), r.imageUrl()))
                .toList();

        PresignedUrlResponse response = new PresignedUrlResponse(items);
        return ResponseEntity.ok(BaseResponse.success("OK", "Presigned URL 발급 성공", response));
    }
}