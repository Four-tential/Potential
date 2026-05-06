package four_tential.potential.infra.ai.etl;

import four_tential.potential.common.dto.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "정책 문서 ETL", description = "RAG 정책 문서 재적재 (관리자 전용)")
@RestController
@RequestMapping("/v1/admin/chatbot/policy-documents")
@Profile("!test")
@RequiredArgsConstructor
public class DocumentEtlController {

    private final DocumentEtlService documentEtlService;

    @Operation(
            summary = "정책 문서 재적재",
            description = "classpath의 정책 md 파일을 검사해 변경된 파일만 임베딩 후 벡터 저장소에 적재"
    )
    @PostMapping("/reload")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BaseResponse<ReloadResult>> reload() {
        ReloadResult result = documentEtlService.loadPolicyDocuments();
        return ResponseEntity.ok(BaseResponse.success(
                HttpStatus.OK.name(),
                "정책 문서 재적재 완료",
                result
        ));
    }
}
