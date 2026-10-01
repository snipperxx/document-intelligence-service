package io.github.snipperxx.docintel.document;

import io.github.snipperxx.docintel.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publishes to Kafka only after the database transaction committed, so a consumer can never
 * receive the id of a document that does not exist yet.
 *
 * <p>The message carries just the document id ("thin event"): the consumer reads the current
 * state from the database, so stale or duplicated messages cannot overwrite newer data.
 * If the broker is down after commit, the document stays UPLOADED and can be re-sent through
 * the reprocess endpoint; a transactional outbox would close that gap fully.
 */
@Component
public class DocumentEventRelay {

    private static final Logger log = LoggerFactory.getLogger(DocumentEventRelay.class);

    private final KafkaTemplate<String, String> kafka;
    private final String topic;

    public DocumentEventRelay(KafkaTemplate<String, String> kafka, AppProperties properties) {
        this.kafka = kafka;
        this.topic = properties.kafka().documentUploadedTopic();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDocumentUploaded(DocumentUploadedEvent event) {
        String id = event.documentId().toString();
        // Keyed by document id, so all events of one document land on the same partition in order.
        kafka.send(topic, id, id).whenComplete((result, error) -> {
            if (error != null) {
                log.error("Could not publish document {} to {}; it stays UPLOADED until reprocessed", id, topic, error);
            } else {
                log.debug("Published document {} to {}", id, result.getRecordMetadata());
            }
        });
    }
}
