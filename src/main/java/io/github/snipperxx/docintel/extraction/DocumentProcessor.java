package io.github.snipperxx.docintel.extraction;

import io.github.snipperxx.docintel.document.Document;
import io.github.snipperxx.docintel.document.DocumentRepository;
import io.github.snipperxx.docintel.document.DocumentService;
import io.github.snipperxx.docintel.document.Fact;
import io.github.snipperxx.docintel.document.FactRepository;
import io.github.snipperxx.docintel.search.ChunkIndex;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Runs the extraction pipeline for one document: claim, chunk, extract, verify quotes, index, store.
 *
 * <p>LLM calls can take minutes, so they run outside any database transaction. Only the short
 * claim and the final write are transactional; this keeps connections free and avoids long locks.
 */
@Service
public class DocumentProcessor {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessor.class);

    private final DocumentRepository documents;
    private final FactRepository facts;
    private final DocumentService documentService;
    private final TextChunker chunker;
    private final FactExtractor extractor;
    private final ChunkIndex chunkIndex;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final Timer processingTimer;
    private final Counter acceptedFacts;
    private final Counter rejectedFacts;
    private final Counter failedDocuments;

    public DocumentProcessor(DocumentRepository documents, FactRepository facts, DocumentService documentService,
                             TextChunker chunker, FactExtractor extractor, ChunkIndex chunkIndex,
                             TransactionTemplate tx, Clock clock, MeterRegistry meters) {
        this.documents = documents;
        this.facts = facts;
        this.documentService = documentService;
        this.chunker = chunker;
        this.extractor = extractor;
        this.chunkIndex = chunkIndex;
        this.tx = tx;
        this.clock = clock;
        this.processingTimer = Timer.builder("docintel.document.processing")
                .description("Time to extract and index one document").register(meters);
        this.acceptedFacts = Counter.builder("docintel.facts").tag("outcome", "accepted").register(meters);
        this.rejectedFacts = Counter.builder("docintel.facts").tag("outcome", "rejected").register(meters);
        this.failedDocuments = Counter.builder("docintel.documents.failed").register(meters);
    }

    public void process(UUID documentId) {
        Integer claimed = tx.execute(status -> documents.claimForProcessing(documentId, clock.instant()));
        if (claimed == null || claimed == 0) {
            log.info("Document {} is not in UPLOADED state, skipping (duplicate or stale message)", documentId);
            return;
        }
        processingTimer.record(() -> runClaimed(documentId));
    }

    private void runClaimed(UUID documentId) {
        try {
            DocumentService.ProcessingInput input = documentService.loadForProcessing(documentId);
            List<TextChunker.Chunk> chunks = chunker.split(input.content());
            Extraction extraction = extract(chunks);

            chunkIndex.replaceForDocument(documentId, input.projectId(), input.title(), chunks.stream()
                    .map(c -> new ChunkIndex.ChunkInput(c.index(), c.start(), c.end(), c.text()))
                    .toList());

            tx.executeWithoutResult(status -> {
                Document document = documents.findById(documentId).orElseThrow();
                facts.deleteByDocumentId(documentId);
                facts.saveAll(extraction.accepted().stream()
                        .map(f -> new Fact(document, f.subject(), f.predicate(), f.object(), f.quote(),
                                f.start(), f.end()))
                        .toList());
                document.markExtracted(extraction.accepted().size(), extraction.rejected());
            });
            acceptedFacts.increment(extraction.accepted().size());
            rejectedFacts.increment(extraction.rejected());
            log.info("Document {}: {} chunks, {} facts kept, {} rejected", documentId, chunks.size(),
                    extraction.accepted().size(), extraction.rejected());
        } catch (RuntimeException e) {
            failedDocuments.increment();
            log.warn("Processing of document {} failed", documentId, e);
            String reason = e.getClass().getSimpleName() + ": " + e.getMessage();
            tx.executeWithoutResult(status -> documents.findById(documentId).ifPresent(d -> d.markFailed(reason)));
        }
    }

    private Extraction extract(List<TextChunker.Chunk> chunks) {
        List<AnchoredFact> accepted = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int rejected = 0;
        for (TextChunker.Chunk chunk : chunks) {
            for (CandidateFact candidate : extractor.extract(chunk.text())) {
                Optional<AnchoredFact> anchored = anchor(chunk, candidate);
                if (anchored.isEmpty()) {
                    rejected++;
                    continue;
                }
                // Overlapping chunks can yield the same fact twice.
                if (seen.add(anchored.get().dedupKey())) {
                    accepted.add(anchored.get());
                }
            }
        }
        accepted.sort((a, b) -> Integer.compare(a.start(), b.start()));
        return new Extraction(accepted, rejected);
    }

    private static Optional<AnchoredFact> anchor(TextChunker.Chunk chunk, CandidateFact candidate) {
        if (!candidate.isComplete()) {
            return Optional.empty();
        }
        return QuoteAnchor.locate(chunk.text(), candidate.quote())
                .map(span -> new AnchoredFact(candidate.subject().strip(), candidate.predicate().strip(),
                        candidate.object().strip(), chunk.text().substring(span.start(), span.end()),
                        chunk.start() + span.start(), chunk.start() + span.end()));
    }

    /**
     * The quote stored is the matched source span, not the model's text, so it is always verbatim.
     */
    record AnchoredFact(String subject, String predicate, String object, String quote, int start, int end) {
        String dedupKey() {
            return (subject + "|" + predicate + "|" + object).toLowerCase(Locale.ROOT) + "@" + start;
        }
    }

    private record Extraction(List<AnchoredFact> accepted, int rejected) {
    }
}
