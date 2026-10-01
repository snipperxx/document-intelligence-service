package io.github.snipperxx.docintel.document;

import java.util.UUID;

/**
 * In-process event raised inside the upload transaction and relayed to Kafka after commit.
 */
public record DocumentUploadedEvent(UUID documentId) {
}
