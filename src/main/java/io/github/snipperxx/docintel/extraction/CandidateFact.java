package io.github.snipperxx.docintel.extraction;

/**
 * A fact as proposed by the model, before its quote has been verified against the source.
 */
public record CandidateFact(String subject, String predicate, String object, String quote) {

    public boolean isComplete() {
        return notBlank(subject) && notBlank(predicate) && notBlank(object) && notBlank(quote);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
