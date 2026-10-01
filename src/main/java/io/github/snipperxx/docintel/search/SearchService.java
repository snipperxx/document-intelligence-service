package io.github.snipperxx.docintel.search;

import io.github.snipperxx.docintel.config.AppProperties;
import io.github.snipperxx.docintel.org.AccessService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Semantic search and retrieval-augmented answers, always scoped to the projects the caller can see.
 * Access is applied as a filter inside the vector query, not after it, so a user's top-k results
 * are never filled with chunks they would have to be denied.
 */
@Service
public class SearchService {

    static final String ANSWER_SYSTEM_PROMPT = """
            You answer questions using only the numbered sources provided.
            Cite every claim with the source number in square brackets, for example [2].
            If the sources do not contain the answer, say that you could not find it in the documents.
            """;
    static final String NO_SOURCES_ANSWER = "I could not find anything about this in the documents you can access.";

    private final ChunkIndex chunkIndex;
    private final AccessService access;
    private final ChatClient chatClient;
    private final AppProperties.Search properties;

    public SearchService(ChunkIndex chunkIndex, AccessService access, ChatClient.Builder chatClientBuilder,
                         AppProperties properties) {
        this.chunkIndex = chunkIndex;
        this.access = access;
        this.chatClient = chatClientBuilder.defaultSystem(ANSWER_SYSTEM_PROMPT).build();
        this.properties = properties.search();
    }

    public List<ChunkIndex.ChunkHit> search(long userId, String query, Integer topK) {
        return chunkIndex.search(query, access.visibleProjectIds(userId), effectiveTopK(topK));
    }

    public Answer ask(long userId, String question, Integer topK) {
        List<ChunkIndex.ChunkHit> sources = search(userId, question, topK);
        if (sources.isEmpty()) {
            return new Answer(NO_SOURCES_ANSWER, List.of());
        }
        String numberedSources = IntStream.range(0, sources.size())
                .mapToObj(i -> "[" + (i + 1) + "] (" + sources.get(i).title() + ")\n" + sources.get(i).text())
                .collect(Collectors.joining("\n\n"));
        String answer = chatClient.prompt()
                .user(user -> user.text("Sources:\n{sources}\n\nQuestion: {question}")
                        .param("sources", numberedSources)
                        .param("question", question))
                .call()
                .content();
        return new Answer(answer, sources);
    }

    private int effectiveTopK(Integer requested) {
        int value = requested == null ? properties.defaultTopK() : requested;
        return Math.clamp(value, 1, properties.maxTopK());
    }

    /**
     * Sources are listed in the order they were numbered in the prompt, so [n] refers to sources[n-1].
     */
    public record Answer(String answer, List<ChunkIndex.ChunkHit> sources) {
    }
}
