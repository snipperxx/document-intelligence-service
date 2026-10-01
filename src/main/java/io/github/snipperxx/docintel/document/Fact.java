package io.github.snipperxx.docintel.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A subject-predicate-object statement whose quote was found verbatim (modulo whitespace and case)
 * in the source text at [startOffset, endOffset).
 */
@Entity
@Table(name = "fact")
public class Fact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id")
    private Document document;

    @Column(nullable = false, length = 500)
    private String subject;

    @Column(nullable = false, length = 500)
    private String predicate;

    @Column(nullable = false, length = 1000)
    private String object;

    @Column(nullable = false, columnDefinition = "text")
    private String quote;

    @Column(name = "start_offset", nullable = false)
    private int startOffset;

    @Column(name = "end_offset", nullable = false)
    private int endOffset;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Fact() {
    }

    public Fact(Document document, String subject, String predicate, String object, String quote,
                int startOffset, int endOffset) {
        this.document = document;
        this.subject = truncate(subject, 500);
        this.predicate = truncate(predicate, 500);
        this.object = truncate(object, 1000);
        this.quote = quote;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.createdAt = Instant.now();
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    public Long getId() {
        return id;
    }

    public String getSubject() {
        return subject;
    }

    public String getPredicate() {
        return predicate;
    }

    public String getObject() {
        return object;
    }

    public String getQuote() {
        return quote;
    }

    public int getStartOffset() {
        return startOffset;
    }

    public int getEndOffset() {
        return endOffset;
    }
}
