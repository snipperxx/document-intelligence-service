package io.github.snipperxx.docintel.document;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentLifecycleTest {

    private static final Duration STALE_AFTER = Duration.ofMinutes(30);
    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    @Test
    void activeProcessingCannotBeReset() {
        Document document = processingSince(NOW.minus(Duration.ofMinutes(5)));

        assertThat(document.isBeingProcessed(NOW, STALE_AFTER)).isTrue();
        assertThatThrownBy(() -> document.resetForReprocessing(NOW, STALE_AFTER))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void abandonedProcessingCanBeTakenOver() {
        Document document = processingSince(NOW.minus(Duration.ofHours(2)));

        document.resetForReprocessing(NOW, STALE_AFTER);

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.UPLOADED);
    }

    @Test
    void failedDocumentsCanBeReset() {
        Document document = newDocument();
        document.markFailed("model unavailable");

        document.resetForReprocessing(NOW, STALE_AFTER);

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.UPLOADED);
        assertThat(document.getFailureReason()).isNull();
    }

    @Test
    void longFailureReasonsAreTruncatedToTheColumnSize() {
        Document document = newDocument();

        document.markFailed("x".repeat(5000));

        assertThat(document.getFailureReason()).hasSize(Document.MAX_FAILURE_REASON);
    }

    private static Document processingSince(Instant since) {
        Document document = newDocument();
        ReflectionTestUtils.setField(document, "status", DocumentStatus.PROCESSING);
        ReflectionTestUtils.setField(document, "updatedAt", since);
        return document;
    }

    private static Document newDocument() {
        return new Document(null, "Title", "text/plain", "content", null);
    }
}
