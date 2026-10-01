package io.github.snipperxx.docintel.document;

/**
 * UPLOADED -> PROCESSING -> EXTRACTED | FAILED. Reprocessing moves a document back to UPLOADED.
 */
public enum DocumentStatus {
    UPLOADED,
    PROCESSING,
    EXTRACTED,
    FAILED
}
