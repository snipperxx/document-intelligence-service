package io.github.snipperxx.docintel.extraction;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Asks the configured chat model (a local Ollama model by default) for structured output and
 * maps it onto {@link CandidateFact}s. Spring AI adds the JSON schema of {@link ExtractionResult}
 * to the prompt and parses the reply.
 */
@Component
public class LlmFactExtractor implements FactExtractor {

    static final String SYSTEM_PROMPT = """
            You extract atomic facts from a text.
            An atomic fact is a single subject-predicate-object statement that cannot be split further.
            Rules:
            - Only state what the text says. Do not use outside knowledge and do not infer.
            - For every fact, "quote" must be copied character for character from the text:
              the shortest fragment that supports the fact. Never paraphrase the quote.
            - Keep subject, predicate and object short. Resolve pronouns to the entity they refer to.
            - Return at most 30 facts. Return an empty list if the text states no facts.
            """;

    private final ChatClient chatClient;

    public LlmFactExtractor(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.defaultSystem(SYSTEM_PROMPT).build();
    }

    @Override
    public List<CandidateFact> extract(String text) {
        ExtractionResult result = chatClient.prompt()
                .user(user -> user.text("Text:\n<<<\n{text}\n>>>").param("text", text))
                .call()
                .entity(ExtractionResult.class);
        if (result == null || result.facts() == null) {
            return List.of();
        }
        return result.facts();
    }

    record ExtractionResult(List<CandidateFact> facts) {
    }
}
