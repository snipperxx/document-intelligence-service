package io.github.snipperxx.docintel.document;

import io.github.snipperxx.docintel.auth.AppUser;
import io.github.snipperxx.docintel.org.OrgUnit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document")
public class Document {

    static final int MAX_FAILURE_REASON = 1000;

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id")
    private OrgUnit project;

    @Column(nullable = false)
    private String title;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentStatus status;

    @Column(name = "failure_reason", length = MAX_FAILURE_REASON)
    private String failureReason;

    @Column(name = "fact_count", nullable = false)
    private int factCount;

    @Column(name = "rejected_count", nullable = false)
    private int rejectedCount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by")
    private AppUser uploadedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Document() {
    }

    public Document(OrgUnit project, String title, String contentType, String content, AppUser uploadedBy) {
        this.id = UUID.randomUUID();
        this.project = project;
        this.title = title;
        this.contentType = contentType;
        this.content = content;
        this.uploadedBy = uploadedBy;
        this.status = DocumentStatus.UPLOADED;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void markExtracted(int factCount, int rejectedCount) {
        this.status = DocumentStatus.EXTRACTED;
        this.factCount = factCount;
        this.rejectedCount = rejectedCount;
        this.failureReason = null;
        this.updatedAt = Instant.now();
    }

    public void markFailed(String reason) {
        this.status = DocumentStatus.FAILED;
        this.failureReason = reason == null || reason.length() <= MAX_FAILURE_REASON
                ? reason : reason.substring(0, MAX_FAILURE_REASON);
        this.updatedAt = Instant.now();
    }

    /**
     * A PROCESSING claim older than {@code staleAfter} is treated as abandoned (for example the
     * worker crashed or was redeployed mid-document) and may be taken over.
     */
    public boolean isBeingProcessed(Instant now, Duration staleAfter) {
        return status == DocumentStatus.PROCESSING && updatedAt.isAfter(now.minus(staleAfter));
    }

    public void resetForReprocessing(Instant now, Duration staleAfter) {
        if (isBeingProcessed(now, staleAfter)) {
            throw new IllegalStateException("Document is currently being processed");
        }
        this.status = DocumentStatus.UPLOADED;
        this.failureReason = null;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public OrgUnit getProject() {
        return project;
    }

    public String getTitle() {
        return title;
    }

    public String getContentType() {
        return contentType;
    }

    public String getContent() {
        return content;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public int getFactCount() {
        return factCount;
    }

    public int getRejectedCount() {
        return rejectedCount;
    }

    public AppUser getUploadedBy() {
        return uploadedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
