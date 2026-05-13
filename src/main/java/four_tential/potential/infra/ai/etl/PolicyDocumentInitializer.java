package four_tential.potential.infra.ai.etl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class PolicyDocumentInitializer implements ApplicationRunner {

    private final DocumentEtlService documentEtlService;

    @Override
    public void run(@NonNull ApplicationArguments args) {
        try {
            ReloadResult result = documentEtlService.loadPolicyDocuments();
            log.info("정책 문서 초기 적재 완료 - added={}, updated={}, unchanged={}, removed={}, totalChunks={}",
                    result.added(), result.updated(), result.unchanged(), result.removed(), result.totalChunks());
        } catch (Exception e) {
            log.error("정책 문서 초기 적재 실패 - 앱은 정상 기동됩니다", e);
        }
    }
}
