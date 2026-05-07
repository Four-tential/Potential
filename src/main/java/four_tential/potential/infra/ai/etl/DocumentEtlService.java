package four_tential.potential.infra.ai.etl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@Profile("!test")
@RequiredArgsConstructor
public class DocumentEtlService {

    private static final String POLICY_RESOURCE_PATTERN = "classpath:ai/rag/*.md";
    private static final String DOMAIN_POLICY = "policy";
    private static final Pattern SECTION_PATTERN = Pattern.compile("^## (.+)$", Pattern.MULTILINE);

    private final VectorStore vectorStore;
    private final PolicyDocumentRepository policyDocumentRepository;

    @Transactional
    public ReloadResult loadPolicyDocuments() {
        Map<String, FilePayload> diskFiles = readDiskPolicyFiles();
        Map<String, PolicyDocument> existingBySource = policyDocumentRepository.findAll().stream()
                .collect(Collectors.toMap(doc -> doc.getSource(), doc -> doc));

        int added = 0;
        int updated = 0;
        int unchanged = 0;
        int removed = 0;
        int totalChunks = 0;

        for (Map.Entry<String, FilePayload> entry : diskFiles.entrySet()) {
            String source = entry.getKey();
            FilePayload payload = entry.getValue();
            PolicyDocument meta = existingBySource.get(source);

            if (meta != null && meta.getContentHash().equals(payload.hash())) {
                unchanged++;
                totalChunks += meta.getChunkCount();
                continue;
            }

            List<Document> chunks = splitByMarkdownHeader(payload.content(), source);
            if (chunks.isEmpty()) {
                log.warn("정책 문서 청크 0개 - 스킵: {}", source);
                continue;
            }

            if (meta != null) {
                deleteVectorChunksBySource(source);
                vectorStore.add(chunks);
                meta.update(payload.hash(), chunks.size());
                updated++;
                log.info("정책 문서 갱신: {} - {}개 청크", source, chunks.size());
            } else {
                vectorStore.add(chunks);
                policyDocumentRepository.save(PolicyDocument.of(source, payload.hash(), chunks.size()));
                added++;
                log.info("정책 문서 신규 적재: {} - {}개 청크", source, chunks.size());
            }
            totalChunks += chunks.size();
        }

        Set<String> diskSources = diskFiles.keySet();
        for (Map.Entry<String, PolicyDocument> entry : existingBySource.entrySet()) {
            String source = entry.getKey();
            if (!diskSources.contains(source)) {
                deleteVectorChunksBySource(source);
                policyDocumentRepository.delete(entry.getValue());
                removed++;
                log.info("정책 문서 제거: {}", source);
            }
        }

        log.info("정책 문서 적재 결과 - added={}, updated={}, unchanged={}, removed={}, totalChunks={}",
                added, updated, unchanged, removed, totalChunks);
        return new ReloadResult(added, updated, unchanged, removed, totalChunks);
    }

    private Map<String, FilePayload> readDiskPolicyFiles() {
        Map<String, FilePayload> result = new LinkedHashMap<>();
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources(POLICY_RESOURCE_PATTERN);
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null) {
                    continue;
                }
                String source = filename.endsWith(".md")
                        ? filename.substring(0, filename.length() - ".md".length())
                        : filename;
                String content = resource.getContentAsString(StandardCharsets.UTF_8);
                String hash = sha256(content);
                result.put(source, new FilePayload(hash, content));
            }
        } catch (IOException e) {
            throw new IllegalStateException("정책 문서 로딩 실패", e);
        }
        return result;
    }

    private List<Document> splitByMarkdownHeader(String content, String source) {
        List<Document> documents = new ArrayList<>();

        String title = extractTitle(content);

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
                    "source", source != null ? source : "unknown",
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

    private void deleteVectorChunksBySource(String source) {
        try {
            FilterExpressionBuilder builder = new FilterExpressionBuilder();
            Filter.Expression filter = builder.and(
                    builder.eq("domain", DOMAIN_POLICY),
                    builder.eq("source", source)
            ).build();
            vectorStore.delete(filter);
        } catch (Exception e) {
            log.error("벡터 청크 삭제 실패 - source={}", source, e);
            throw new IllegalStateException("벡터 청크 삭제 실패: " + source, e);
        }
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 미지원", e);
        }
    }

    private record FilePayload(String hash, String content) {
    }
}
