package io.github.snipperxx.docintel.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface FactRepository extends JpaRepository<Fact, Long> {

    List<Fact> findByDocumentIdOrderByStartOffset(UUID documentId);

    @Modifying
    @Query("delete from Fact f where f.document.id = :documentId")
    int deleteByDocumentId(UUID documentId);
}
