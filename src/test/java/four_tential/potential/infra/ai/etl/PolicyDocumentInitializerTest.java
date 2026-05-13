package four_tential.potential.infra.ai.etl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PolicyDocumentInitializerTest {

    private DocumentEtlService documentEtlService;
    private PolicyDocumentInitializer initializer;

    @BeforeEach
    void setUp() {
        documentEtlService = mock(DocumentEtlService.class);
        initializer = new PolicyDocumentInitializer(documentEtlService);
    }

    @Test
    @DisplayName("run — 정상 적재 시 service.loadPolicyDocuments 호출")
    void run_invokes_load() {
        when(documentEtlService.loadPolicyDocuments())
                .thenReturn(new ReloadResult(8, 0, 0, 0, 24));

        initializer.run(new DefaultApplicationArguments());

        verify(documentEtlService).loadPolicyDocuments();
    }

    @Test
    @DisplayName("run — 적재 중 예외가 나도 앱 기동에 지장 없도록 swallow")
    void run_swallows_exception() {
        when(documentEtlService.loadPolicyDocuments())
                .thenThrow(new RuntimeException("벡터 저장소 연결 실패"));

        initializer.run(new DefaultApplicationArguments());

        verify(documentEtlService).loadPolicyDocuments();
    }
}
