package io.github.snipperxx.docintel.extraction;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TextChunkerTest {

    private final TextChunker chunker = new TextChunker(200, 40);

    @Test
    void shortTextIsOneChunk() {
        List<TextChunker.Chunk> chunks = chunker.split("A short text.");

        assertThat(chunks).singleElement().satisfies(c -> {
            assertThat(c.start()).isZero();
            assertThat(c.end()).isEqualTo(13);
        });
    }

    @Test
    void chunksMapBackToTheSourceText() {
        String text = sampleText(30);

        for (TextChunker.Chunk chunk : chunker.split(text)) {
            assertThat(text.substring(chunk.start(), chunk.end())).isEqualTo(chunk.text());
            assertThat(chunk.text().length()).isLessThanOrEqualTo(200);
        }
    }

    @Test
    void chunksCoverTheWholeTextWithOverlap() {
        String text = sampleText(30);
        List<TextChunker.Chunk> chunks = chunker.split(text);

        assertThat(chunks.getFirst().start()).isZero();
        assertThat(chunks.getLast().end()).isEqualTo(text.length());
        for (int i = 1; i < chunks.size(); i++) {
            assertThat(chunks.get(i).start())
                    .as("chunk %d starts before the previous one ends", i)
                    .isLessThan(chunks.get(i - 1).end());
            assertThat(chunks.get(i).index()).isEqualTo(i);
        }
    }

    @Test
    void prefersSentenceBoundaries() {
        String text = sampleText(30);

        List<TextChunker.Chunk> chunks = chunker.split(text);

        chunks.subList(0, chunks.size() - 1)
                .forEach(c -> assertThat(c.text()).endsWith(". "));
    }

    @Test
    void splitsTextWithoutAnyBoundary() {
        String text = "x".repeat(450);

        List<TextChunker.Chunk> chunks = chunker.split(text);

        assertThat(chunks).hasSizeGreaterThanOrEqualTo(3);
        assertThat(chunks.getLast().end()).isEqualTo(450);
    }

    @Test
    void rejectsOverlapThatWouldNotMakeProgress() {
        assertThatThrownBy(() -> new TextChunker(200, 100)).isInstanceOf(IllegalArgumentException.class);
    }

    private static String sampleText(int sentences) {
        return IntStream.range(0, sentences)
                .mapToObj(i -> "Sentence number " + i + " says something useful")
                .collect(Collectors.joining(". ", "", "."));
    }
}
