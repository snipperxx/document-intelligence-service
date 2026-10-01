package io.github.snipperxx.docintel.document;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Page<Document> findByProjectIdOrderByCreatedAtDesc(Long projectId, Pageable pageable);

    /**
     * Atomically moves an UPLOADED document to PROCESSING. Returns 0 when another consumer already
     * claimed it or it was processed before, which makes redelivered Kafka messages harmless.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            update Document d
               set d.status = io.github.snipperxx.docintel.document.DocumentStatus.PROCESSING,
                   d.updatedAt = :now,
                   d.version = d.version + 1
             where d.id = :id
               and d.status = io.github.snipperxx.docintel.document.DocumentStatus.UPLOADED
            """)
    int claimForProcessing(UUID id, Instant now);
}
