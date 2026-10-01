package io.github.snipperxx.docintel.extraction;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the real ChatClient structured-output path against a scripted model reply.
 */
class LlmFactExtractorTest {

    @Test
    void parsesStructuredReplyIntoCandidateFacts() {
        var model = new ScriptedChatModel("""
                {"facts": [
                  {"subject": "Acme Corp", "predicate": "employs", "object": "120 engineers",
                   "quote": "Acme Corp employs 120 engineers"},
                  {"subject": "Acme Corp", "predicate": "is based in", "object": "Berlin",
                   "quote": "based in Berlin"}
                ]}
                """);
        var extractor = new LlmFactExtractor(ChatClient.builder(model));

        List<CandidateFact> facts = extractor.extract("Acme Corp employs 120 engineers and is based in Berlin.");

        assertThat(facts).containsExactly(
                new CandidateFact("Acme Corp", "employs", "120 engineers", "Acme Corp employs 120 engineers"),
                new CandidateFact("Acme Corp", "is based in", "Berlin", "based in Berlin"));
    }

    @Test
    void sendsRulesAsSystemPromptAndTextAsUserMessage() {
        var model = new ScriptedChatModel("{\"facts\": []}");
        var extractor = new LlmFactExtractor(ChatClient.builder(model));

        extractor.extract("Some {curly} text.");

        Prompt prompt = model.lastPrompt.get();
        assertThat(prompt.getInstructions())
                .filteredOn(m -> m.getMessageType() == MessageType.SYSTEM).singleElement()
                .satisfies(m -> assertThat(m.getText()).contains("character for character"));
        assertThat(prompt.getUserMessage().getText()).contains("Some {curly} text.");
    }

    @Test
    void emptyReplyMeansNoFacts() {
        var extractor = new LlmFactExtractor(ChatClient.builder(new ScriptedChatModel("{}")));

        assertThat(extractor.extract("Nothing here.")).isEmpty();
    }

    private static class ScriptedChatModel implements ChatModel {

        private final String reply;
        private final AtomicReference<Prompt> lastPrompt = new AtomicReference<>();

        ScriptedChatModel(String reply) {
            this.reply = reply;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            lastPrompt.set(prompt);
            return new ChatResponse(List.of(new Generation(new AssistantMessage(reply))));
        }
    }
}
