package four_tential.potential.infra.ai.test;

import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.infra.ai.embedding.ReviewEmbeddingService;
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
 *   3. POST /ai/test/embed   → 리뷰 임베딩 저장
 *   4. GET  /ai/test/search  → 유사 리뷰 검색
 *   5. pgvector DB에서 직접 확인:
 *      SELECT id, content, metadata FROM potential_vector_store LIMIT 10;
 */
@RestController
@RequestMapping("/ai/test")
@RequiredArgsConstructor
@Profile("local")   // ← local 프로파일에서만 Bean 등록
public class EmbeddingTestController {

    private final ReviewEmbeddingService reviewEmbeddingService;

    //단일 리뷰 임베딩 저장
    @PostMapping("/embed")
    public ResponseEntity<BaseResponse<String>> embed(@RequestBody EmbedRequest request) {
        reviewEmbeddingService.embedReview(request.courseId(), request.content());
        return ResponseEntity.ok(BaseResponse.success(
                "200",
                "임베딩 저장 완료",
                "courseId=%d 리뷰가 potential_vector_store에 저장되었습니다.".formatted(request.courseId())
        ));
    }


    //배치 임베딩 저장
    @PostMapping("/embed/batch")
    public ResponseEntity<BaseResponse<String>> embedBatch(@RequestBody EmbedBatchRequest request) {
        reviewEmbeddingService.embedReviews(request.courseId(), request.reviews());
        return ResponseEntity.ok(BaseResponse.success(
                "200",
                "배치 임베딩 저장 완료",
                "%d건 저장 완료 (courseId=%d)".formatted(request.reviews().size(), request.courseId())
        ));
    }

    //유사 리뷰 검색
    @GetMapping("/search")
    public ResponseEntity<BaseResponse<List<String>>> search(
            @RequestParam Long courseId,
            @RequestParam String query
    ) {
        List<String> results = reviewEmbeddingService.searchSimilarReviews(courseId, query);
        return ResponseEntity.ok(BaseResponse.success(
                "200",
                "유사 리뷰 검색 완료 (%d건)".formatted(results.size()),
                results
        ));
    }

    //  Request Records
    record EmbedRequest(Long courseId, String content) {}
    record EmbedBatchRequest(Long courseId, List<String> reviews) {}
}