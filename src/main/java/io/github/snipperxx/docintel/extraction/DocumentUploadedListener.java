package io.github.snipperxx.docintel.extraction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class DocumentUploadedListener {

    private static final Logger log = LoggerFactory.getLogger(DocumentUploadedListener.class);

    private final DocumentProcessor processor;

    public DocumentUploadedListener(DocumentProcessor processor) {
        this.processor = processor;
    }

    @KafkaListener(topics = "${app.kafka.document-uploaded-topic}", groupId = "${spring.application.name}-processor")
    public void onDocumentUploaded(String documentId) {
        UUID id;
        try {
            id = UUID.fromString(documentId);
        } catch (IllegalArgumentException e) {
            // A malformed message can never succeed; log and move on instead of blocking the partition.
            log.error("Ignoring message with invalid document id '{}'", documentId);
            return;
        }
        processor.process(id);
    }
}
