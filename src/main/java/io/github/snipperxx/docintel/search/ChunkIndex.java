package io.github.snipperxx.docintel.search;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Semantic index over document chunks, backed by Spring AI's pgvector store.
 * Each row carries the project id, so searches can be restricted to what the caller may see.
 */
@Component
public class ChunkIndex {

    static final String DOCUMENT_ID = "documentId";
    static final String PROJECT_ID = "projectId";
    static final String TITLE = "title";
    static final String CHUNK_INDEX = "chunkIndex";
    static final String START_OFFSET = "startOffset";
    static final String END_OFFSET = "endOffset";

    private final VectorStore vectorStore;

    public ChunkIndex(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public void replaceForDocument(UUID documentId, long projectId, String title, List<ChunkInput> chunks) {
        deleteForDocument(documentId);
        if (chunks.isEmpty()) {
            return;
        }
        List<Document> rows = new ArrayList<>(chunks.size());
        for (ChunkInput chunk : chunks) {
            rows.add(Document.builder()
                    .id(rowId(documentId, chunk.index()))
                    .text(chunk.text())
                    .metadata(Map.of(
                            DOCUMENT_ID, documentId.toString(),
                            PROJECT_ID, projectId,
                            TITLE, title,
                            CHUNK_INDEX, chunk.index(),
                            START_OFFSET, chunk.start(),
                            END_OFFSET, chunk.end()))
                    .build());
        }
        vectorStore.add(rows);
    }

    public void deleteForDocument(UUID documentId) {
        vectorStore.delete(new FilterExpressionBuilder().eq(DOCUMENT_ID, documentId.toString()).build());
    }

    public List<ChunkHit> search(String query, Collection<Long> projectIds, int topK) {
        if (projectIds.isEmpty()) {
            return List.of();
        }
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .filterExpression(new FilterExpressionBuilder()
                        .in(PROJECT_ID, new ArrayList<Object>(projectIds))
                        .build())
                .build();
        return vectorStore.similaritySearch(request).stream().map(ChunkIndex::toHit).toList();
    }

    private static ChunkHit toHit(Document row) {
        Map<String, Object> m = row.getMetadata();
        return new ChunkHit(
                UUID.fromString(String.valueOf(m.get(DOCUMENT_ID))),
                asLong(m.get(PROJECT_ID)),
                String.valueOf(m.get(TITLE)),
                (int) asLong(m.get(CHUNK_INDEX)),
                (int) asLong(m.get(START_OFFSET)),
                (int) asLong(m.get(END_OFFSET)),
                row.getText(),
                row.getScore() == null ? 0.0 : row.getScore());
    }

    private static long asLong(Object value) {
        return value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value));
    }

    /**
     * Deterministic row id, so re-indexing a document overwrites instead of duplicating.
     */
    static String rowId(UUID documentId, int chunkIndex) {
        return UUID.nameUUIDFromBytes((documentId + ":" + chunkIndex).getBytes(StandardCharsets.UTF_8)).toString();
    }

    public record ChunkInput(int index, int start, int end, String text) {
    }

    public record ChunkHit(UUID documentId, long projectId, String title, int chunkIndex, int startOffset,
                           int endOffset, String text, double score) {
    }
}
