package io.github.snipperxx.docintel.extraction;

import io.github.snipperxx.docintel.config.AppProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits text into overlapping windows that prefer to end at paragraph, then sentence, then word
 * boundaries. Every chunk keeps its offsets in the original text, so facts found inside a chunk
 * can be mapped back to the full document.
 */
@Component
public class TextChunker {

    private final int maxChars;
    private final int overlapChars;

    @Autowired
    public TextChunker(AppProperties properties) {
        this(properties.chunking().maxChars(), properties.chunking().overlapChars());
    }

    TextChunker(int maxChars, int overlapChars) {
        if (overlapChars >= maxChars / 2) {
            throw new IllegalArgumentException("overlap must be smaller than half of maxChars");
        }
        this.maxChars = maxChars;
        this.overlapChars = overlapChars;
    }

    public List<Chunk> split(String text) {
        List<Chunk> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = chooseEnd(text, start);
            String slice = text.substring(start, end);
            if (!slice.isBlank()) {
                chunks.add(new Chunk(chunks.size(), start, end, slice));
            }
            if (end >= text.length()) {
                break;
            }
            start = nextStart(text, start, end);
        }
        return chunks;
    }

    private int chooseEnd(String text, int start) {
        int hardEnd = Math.min(start + maxChars, text.length());
        if (hardEnd == text.length()) {
            return hardEnd;
        }
        // Do not accept a boundary in the first half of the window: tiny chunks embed poorly.
        int floor = start + maxChars / 2;
        int boundary = lastBoundary(text, "\n\n", floor, hardEnd);
        if (boundary < 0) {
            boundary = Math.max(lastBoundary(text, ". ", floor, hardEnd), lastBoundary(text, "\n", floor, hardEnd));
        }
        if (boundary < 0) {
            boundary = lastBoundary(text, " ", floor, hardEnd);
        }
        return boundary < 0 ? hardEnd : boundary;
    }

    /**
     * Position just after the last occurrence of the separator that ends inside [floor, limit].
     */
    private static int lastBoundary(String text, String separator, int floor, int limit) {
        int index = text.lastIndexOf(separator, limit - separator.length());
        if (index < 0) {
            return -1;
        }
        int boundary = index + separator.length();
        return boundary >= floor && boundary <= limit ? boundary : -1;
    }

    private int nextStart(String text, int start, int end) {
        int candidate = Math.max(end - overlapChars, start + 1);
        // Start the overlap on a word boundary so the next chunk does not begin mid-word.
        int space = text.indexOf(' ', candidate);
        if (space >= 0 && space < end) {
            candidate = space + 1;
        }
        return candidate;
    }

    public record Chunk(int index, int start, int end, String text) {
    }
}
