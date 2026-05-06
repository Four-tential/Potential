package four_tential.potential.infra.ai.etl;

public record ReloadResult(
        int added,
        int updated,
        int unchanged,
        int removed,
        int totalChunks
) {
}
