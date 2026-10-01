package io.github.snipperxx.docintel.document;

import io.github.snipperxx.docintel.auth.AppUserRepository;
import io.github.snipperxx.docintel.common.BadRequestException;
import io.github.snipperxx.docintel.common.ConflictException;
import io.github.snipperxx.docintel.common.NotFoundException;
import io.github.snipperxx.docintel.common.PageResponse;
import io.github.snipperxx.docintel.org.AccessService;
import io.github.snipperxx.docintel.org.OrgUnit;
import io.github.snipperxx.docintel.org.OrgUnitType;
import io.github.snipperxx.docintel.org.Role;
import io.github.snipperxx.docintel.search.ChunkIndex;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentService {

    /**
     * How long a PROCESSING claim is honoured before reprocess or delete may take over.
     */
    static final Duration STALE_PROCESSING = Duration.ofMinutes(30);

    private final DocumentRepository documents;
    private final FactRepository facts;
    private final AppUserRepository users;
    private final AccessService access;
    private final TextExtractor textExtractor;
    private final ChunkIndex chunkIndex;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public DocumentService(DocumentRepository documents, FactRepository facts, AppUserRepository users,
                           AccessService access, TextExtractor textExtractor, ChunkIndex chunkIndex,
                           ApplicationEventPublisher events, Clock clock) {
        this.documents = documents;
        this.facts = facts;
        this.users = users;
        this.access = access;
        this.textExtractor = textExtractor;
        this.chunkIndex = chunkIndex;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public DocumentView upload(long userId, long projectId, String title, String filename, String contentType,
                               byte[] bytes) {
        OrgUnit project = access.requireUnit(userId, projectId, Role.EDITOR);
        if (project.getType() != OrgUnitType.PROJECT) {
            throw new BadRequestException("Documents can only be uploaded to a PROJECT unit");
        }
        TextExtractor.ExtractedText extracted = textExtractor.extract(filename, contentType, bytes);
        String effectiveTitle = title != null && !title.isBlank() ? title.strip()
                : filename != null && !filename.isBlank() ? filename : "Untitled document";

        Document document = documents.save(new Document(project, effectiveTitle, extracted.contentType(),
                extracted.text(), users.getReferenceById(userId)));
        events.publishEvent(new DocumentUploadedEvent(document.getId()));
        return DocumentView.of(document);
    }

    @Transactional(readOnly = true)
    public PageResponse<DocumentView> list(long userId, long projectId, int page, int size) {
        access.requireUnit(userId, projectId, Role.VIEWER);
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100));
        return PageResponse.of(documents.findByProjectIdOrderByCreatedAtDesc(projectId, pageRequest),
                DocumentView::of);
    }

    @Transactional(readOnly = true)
    public DocumentView get(long userId, UUID documentId) {
        return DocumentView.of(loadAccessible(userId, documentId, Role.VIEWER));
    }

    @Transactional(readOnly = true)
    public String content(long userId, UUID documentId) {
        return loadAccessible(userId, documentId, Role.VIEWER).getContent();
    }

    @Transactional(readOnly = true)
    public List<FactView> facts(long userId, UUID documentId) {
        loadAccessible(userId, documentId, Role.VIEWER);
        return facts.findByDocumentIdOrderByStartOffset(documentId).stream().map(FactView::of).toList();
    }

    @Transactional
    public DocumentView reprocess(long userId, UUID documentId) {
        Document document = loadAccessible(userId, documentId, Role.EDITOR);
        try {
            document.resetForReprocessing(clock.instant(), STALE_PROCESSING);
        } catch (IllegalStateException e) {
            throw new ConflictException(e.getMessage());
        }
        events.publishEvent(new DocumentUploadedEvent(document.getId()));
        return DocumentView.of(document);
    }

    @Transactional
    public void delete(long userId, UUID documentId) {
        Document document = loadAccessible(userId, documentId, Role.EDITOR);
        if (document.isBeingProcessed(clock.instant(), STALE_PROCESSING)) {
            throw new ConflictException("Document is currently being processed");
        }
        chunkIndex.deleteForDocument(documentId);
        documents.delete(document);
    }

    private Document loadAccessible(long userId, UUID documentId, Role required) {
        Document document = documents.findById(documentId)
                .orElseThrow(() -> new NotFoundException("Document " + documentId + " not found"));
        try {
            access.requireUnit(userId, document.getProject().getId(), required);
        } catch (NotFoundException e) {
            // Do not reveal that the document exists in a project the caller cannot see.
            throw new NotFoundException("Document " + documentId + " not found");
        }
        return document;
    }

    public record DocumentView(UUID id, Long projectId, String title, String contentType, DocumentStatus status,
                               String failureReason, int factCount, int rejectedCount, int characters,
                               Instant createdAt, Instant updatedAt) {
        static DocumentView of(Document d) {
            return new DocumentView(d.getId(), d.getProject().getId(), d.getTitle(), d.getContentType(),
                    d.getStatus(), d.getFailureReason(), d.getFactCount(), d.getRejectedCount(),
                    d.getContent().length(), d.getCreatedAt(), d.getUpdatedAt());
        }
    }

    public record FactView(Long id, String subject, String predicate, String object, String quote,
                           int startOffset, int endOffset) {
        static FactView of(Fact f) {
            return new FactView(f.getId(), f.getSubject(), f.getPredicate(), f.getObject(), f.getQuote(),
                    f.getStartOffset(), f.getEndOffset());
        }
    }

    /**
     * Used by the processing pipeline, which runs without a user context.
     */
    @Transactional(readOnly = true)
    public ProcessingInput loadForProcessing(UUID documentId) {
        Document document = documents.findById(documentId)
                .orElseThrow(() -> new NotFoundException("Document " + documentId + " not found"));
        return new ProcessingInput(document.getId(), document.getProject().getId(), document.getTitle(),
                document.getContent());
    }

    public record ProcessingInput(UUID documentId, Long projectId, String title, String content) {
    }
}
