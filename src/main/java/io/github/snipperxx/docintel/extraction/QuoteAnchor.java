package io.github.snipperxx.docintel.extraction;

import java.util.Optional;

/**
 * Finds where an LLM-provided quote occurs in the source text.
 *
 * <p>This is the guard against hallucinated facts: a fact is only kept if its quote can be found.
 * Matching is exact first, then tolerant to whitespace, case and typographic quotes/dashes, which
 * models routinely normalize. Paraphrases are not matched on purpose.
 */
public final class QuoteAnchor {

    static final int MIN_QUOTE_LENGTH = 3;

    private QuoteAnchor() {
    }

    public static Optional<Span> locate(String source, String quote) {
        if (quote == null) {
            return Optional.empty();
        }
        String trimmed = quote.strip();
        if (trimmed.length() < MIN_QUOTE_LENGTH) {
            return Optional.empty();
        }

        int exact = source.indexOf(trimmed);
        if (exact >= 0) {
            return Optional.of(new Span(exact, exact + trimmed.length()));
        }

        Normalized normalizedSource = normalize(source);
        String normalizedQuote = normalize(trimmed).text();
        if (normalizedQuote.length() < MIN_QUOTE_LENGTH) {
            return Optional.empty();
        }
        int index = normalizedSource.text().indexOf(normalizedQuote);
        if (index < 0) {
            return Optional.empty();
        }
        int start = normalizedSource.originalIndex()[index];
        int end = normalizedSource.originalIndex()[index + normalizedQuote.length() - 1] + 1;
        return Optional.of(new Span(start, end));
    }

    /**
     * Lower-cases, folds typographic characters and collapses whitespace runs, while remembering for
     * every output character which input character it came from.
     */
    private static Normalized normalize(String input) {
        StringBuilder out = new StringBuilder(input.length());
        int[] mapping = new int[input.length()];
        boolean previousWasSpace = true; // also drops leading whitespace
        for (int i = 0; i < input.length(); i++) {
            char c = fold(input.charAt(i));
            if (Character.isWhitespace(c)) {
                if (previousWasSpace) {
                    continue;
                }
                c = ' ';
                previousWasSpace = true;
            } else {
                previousWasSpace = false;
            }
            mapping[out.length()] = i;
            out.append(c);
        }
        int length = out.length();
        if (length > 0 && out.charAt(length - 1) == ' ') {
            out.setLength(--length);
        }
        int[] trimmedMapping = new int[length];
        System.arraycopy(mapping, 0, trimmedMapping, 0, length);
        return new Normalized(out.toString(), trimmedMapping);
    }

    private static char fold(char c) {
        return switch (c) {
            case '‘', '’', '‚', '′' -> '\'';
            case '“', '”', '„', '″' -> '"';
            case '‐', '‑', '‒', '–', '—', '−' -> '-';
            case ' ', ' ', ' ' -> ' ';
            default -> Character.toLowerCase(c);
        };
    }

    public record Span(int start, int end) {
    }

    private record Normalized(String text, int[] originalIndex) {
    }
}
