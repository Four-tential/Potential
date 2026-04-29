package four_tential.potential.infra.ai.test;

import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.infra.ai.vector.VectorStoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 임베딩/벡터 저장소 로컬 검증용 임시 Controller
 *
 * local 프로파일에서만 활성화 — 운영 배포 시 자동 제외
 *
 * 사용 순서:
 *   1. docker-compose up pgvector -d
 *   2. 앱 실행 (profiles: local,ollama 또는 local)
 *   3. POST /ai/test/embed        → 단건 저장
 *   4. POST /ai/test/embed/batch  → 배치 저장
 *   5. GET  /ai/test/search       → 유사도 검색
 *   6. DELETE /ai/test            → 삭제
 *   7. pgvector DB에서 직접 확인:
 *      SELECT id, content, metadata FROM potential_vector_store LIMIT 10;
 */
@RestController
@RequestMapping("/ai/test")
@RequiredArgsConstructor
@Profile("local")
public class EmbeddingTestController {

    private final VectorStoreService vectorStoreService;

    @PostMapping("/embed")
    public ResponseEntity<BaseResponse<String>> embed(@RequestBody EmbedRequest request) {
        vectorStoreService.add(request.domain(), request.entityId(), request.content());
        return ResponseEntity.ok(BaseResponse.success(
                "200",
                "임베딩 저장 완료",
                "domain=%s, entityId=%d 저장 완료".formatted(request.domain(), request.entityId())
        ));
    }

    @PostMapping("/embed/batch")
    public ResponseEntity<BaseResponse<String>> embedBatch(@RequestBody EmbedBatchRequest request) {
        vectorStoreService.addBatch(request.domain(), request.entityId(), request.contents());
        return ResponseEntity.ok(BaseResponse.success(
                "200",
                "배치 임베딩 저장 완료",
                "%d건 저장 완료 (domain=%s, entityId=%d)".formatted(
                        request.contents().size(), request.domain(), request.entityId())
        ));
    }

    @GetMapping("/search")
    public ResponseEntity<BaseResponse<List<String>>> search(
            @RequestParam String domain,
            @RequestParam Long entityId,
            @RequestParam String query
    ) {
        List<String> results = vectorStoreService.search(domain, entityId, query);
        return ResponseEntity.ok(BaseResponse.success(
                "200",
                "유사도 검색 완료 (%d건)".formatted(results.size()),
                results
        ));
    }

    @DeleteMapping
    public ResponseEntity<BaseResponse<String>> delete(
            @RequestParam String domain,
            @RequestParam Long entityId
    ) {
        vectorStoreService.delete(domain, entityId);
        return ResponseEntity.ok(BaseResponse.success(
                "200",
                "삭제 완료",
                "domain=%s, entityId=%d 삭제 완료".formatted(domain, entityId)
        ));
    }

    record EmbedRequest(String domain, Long entityId, String content) {}
    record EmbedBatchRequest(String domain, Long entityId, List<String> contents) {}
}
