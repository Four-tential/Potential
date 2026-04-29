package four_tential.potential.infra.ai.etl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@Profile("!test")
@RequiredArgsConstructor
public class DocumentEtlService {

    private static final String POLICY_RESOURCE_PATTERN = "classpath:ai/rag/*.md";
    private static final String DOMAIN_POLICY = "policy";
    private static final Pattern SECTION_PATTERN = Pattern.compile("^## (.+)$", Pattern.MULTILINE);

    private final VectorStore vectorStore;

    public int loadPolicyDocuments() {
        deleteExistingPolicyDocuments();

        List<Document> allChunks = new ArrayList<>();

        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources(POLICY_RESOURCE_PATTERN);

            for (Resource resource : resources) {
                String filename = resource.getFilename();
                String content = resource.getContentAsString(StandardCharsets.UTF_8);
                List<Document> chunks = splitByMarkdownHeader(content, filename);
                allChunks.addAll(chunks);
                log.info("정책 문서 청킹 완료 — {}: {}개 청크", filename, chunks.size());
            }
        } catch (IOException e) {
            throw new RuntimeException("정책 문서 로딩 실패", e);
        }

        if (!allChunks.isEmpty()) {
            vectorStore.add(allChunks);
            log.info("정책 문서 적재 완료 — 총 {}개 청크", allChunks.size());
        }

        return allChunks.size();
    }

    private List<Document> splitByMarkdownHeader(String content, String filename) {
        List<Document> documents = new ArrayList<>();

        String title = extractTitle(content);
        String source = filename != null ? filename.replace(".md", "") : "unknown";

        Matcher matcher = SECTION_PATTERN.matcher(content);
        List<int[]> sectionPositions = new ArrayList<>();
        List<String> sectionHeaders = new ArrayList<>();

        while (matcher.find()) {
            sectionPositions.add(new int[]{matcher.start(), matcher.end()});
            sectionHeaders.add(matcher.group(1).trim());
        }

        for (int i = 0; i < sectionPositions.size(); i++) {
            int bodyStart = sectionPositions.get(i)[1];
            int bodyEnd = (i + 1 < sectionPositions.size())
                    ? sectionPositions.get(i + 1)[0]
                    : content.length();

            String sectionBody = content.substring(bodyStart, bodyEnd).trim();
            if (sectionBody.isEmpty()) {
                continue;
            }

            String header = sectionHeaders.get(i);
            String chunkText = title + " > " + header + "\n" + sectionBody;

            Map<String, Object> metadata = Map.of(
                    "domain", DOMAIN_POLICY,
                    "source", source,
                    "section", header,
                    "title", title
            );

            documents.add(new Document(chunkText, metadata));
        }

        return documents;
    }

    private String extractTitle(String content) {
        if (content.startsWith("# ")) {
            int lineEnd = content.indexOf('\n');
            return lineEnd > 0 ? content.substring(2, lineEnd).trim() : content.substring(2).trim();
        }
        return "unknown";
    }

    private void deleteExistingPolicyDocuments() {
        try {
            vectorStore.delete("domain == '" + DOMAIN_POLICY + "'");
            log.info("기존 정책 문서 삭제 완료");
        } catch (Exception e) {
            log.warn("기존 정책 문서 삭제 중 오류 (첫 적재일 수 있음): {}", e.getMessage());
        }
    }
}
