package io.github.snipperxx.docintel.extraction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class QuoteAnchorTest {

    private static final String SOURCE = "The supplier must deliver   within 30 days.\nPenalties apply after that.";

    @Test
    void findsExactQuote() {
        var span = QuoteAnchor.locate(SOURCE, "Penalties apply").orElseThrow();

        assertThat(SOURCE.substring(span.start(), span.end())).isEqualTo("Penalties apply");
    }

    @Test
    void toleratesWhitespaceAndCaseDifferences() {
        var span = QuoteAnchor.locate(SOURCE, "must deliver within 30 DAYS").orElseThrow();

        assertThat(SOURCE.substring(span.start(), span.end())).isEqualTo("must deliver   within 30 days");
    }

    @Test
    void toleratesLineBreaksInsideQuote() {
        var span = QuoteAnchor.locate(SOURCE, "30 days. penalties").orElseThrow();

        assertThat(SOURCE.substring(span.start(), span.end())).isEqualTo("30 days.\nPenalties");
    }

    @Test
    void foldsTypographicQuotesAndDashes() {
        String source = "It is called \"fast-track\" approval.";

        var span = QuoteAnchor.locate(source, "called “fast–track”").orElseThrow();

        assertThat(source.substring(span.start(), span.end())).isEqualTo("called \"fast-track\"");
    }

    @Test
    void rejectsParaphrase() {
        assertThat(QuoteAnchor.locate(SOURCE, "The supplier has to deliver within a month")).isEmpty();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  ", "ab"})
    void rejectsMissingOrTooShortQuotes(String quote) {
        assertThat(QuoteAnchor.locate(SOURCE, quote)).isEmpty();
    }
}
