package io.github.snipperxx.docintel.support;

import io.github.snipperxx.docintel.extraction.CandidateFact;
import io.github.snipperxx.docintel.extraction.FactExtractor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic stand-ins for the LLM and the embedding model, so integration tests exercise the
 * whole pipeline (Kafka, pgvector, access filters) without a GPU or network access.
 */
@TestConfiguration(proxyBeanMethods = false)
public class FakeAiConfiguration {

    /**
     * Text containing this marker makes the fake extractor throw, to test the failure path.
     */
    public static final String FAIL_MARKER = "FAIL-EXTRACTION";
    /**
     * A quote the fake extractor invents for every chunk; it never occurs in test documents,
     * so it must always be rejected by quote anchoring.
     */
    public static final String HALLUCINATED_QUOTE = "The moon is made of green cheese";
    public static final String FAKE_ANSWER = "According to the documents, see [1].";

    @Bean
    @Primary
    FactExtractor fakeFactExtractor() {
        return new RuleBasedFactExtractor();
    }

    @Bean
    RecordingChatModel fakeChatModel() {
        return new RecordingChatModel();
    }

    @Bean
    EmbeddingModel fakeEmbeddingModel() {
        return new HashingEmbeddingModel();
    }

    /**
     * Turns sentences like "Acme Corp employs 120 engineers." into facts, quoting the sentence verbatim.
     */
    static class RuleBasedFactExtractor implements FactExtractor {

        private static final Pattern SENTENCE = Pattern.compile(
                "([A-Z][\\w-]*(?: [A-Z][\\w-]*)*) (is|was|has|uses|owns|employs|produces|builds) ([^.\\n]+)\\.");

        @Override
        public List<CandidateFact> extract(String text) {
            if (text.contains(FAIL_MARKER)) {
                throw new IllegalStateException("Simulated model failure");
            }
            List<CandidateFact> facts = new ArrayList<>();
            Matcher m = SENTENCE.matcher(text);
            while (m.find()) {
                // Lower-case the quote so the tolerant (non-exact) anchoring path is exercised too.
                facts.add(new CandidateFact(m.group(1), m.group(2), m.group(3), m.group().toLowerCase(Locale.ROOT)));
            }
            facts.add(new CandidateFact("Moon", "is made of", "green cheese", HALLUCINATED_QUOTE));
            return facts;
        }
    }

    public static class RecordingChatModel implements ChatModel {

        private final AtomicReference<Prompt> lastPrompt = new AtomicReference<>();

        @Override
        public ChatResponse call(Prompt prompt) {
            lastPrompt.set(prompt);
            return new ChatResponse(List.of(new Generation(new AssistantMessage(FAKE_ANSWER))));
        }

        public Prompt lastPrompt() {
            return lastPrompt.get();
        }
    }

    /**
     * Bag-of-words vectors hashed into 768 dimensions: texts sharing words get high cosine similarity.
     */
    static class HashingEmbeddingModel implements EmbeddingModel {

        private static final int DIMENSIONS = 768;

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> embeddings = new ArrayList<>();
            List<String> inputs = request.getInstructions();
            for (int i = 0; i < inputs.size(); i++) {
                embeddings.add(new Embedding(vector(inputs.get(i)), i));
            }
            return new EmbeddingResponse(embeddings);
        }

        @Override
        public float[] embed(Document document) {
            return vector(document.getText());
        }

        @Override
        public int dimensions() {
            return DIMENSIONS;
        }

        private static float[] vector(String text) {
            float[] v = new float[DIMENSIONS];
            for (String token : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
                if (token.length() > 2) {
                    v[Math.floorMod(token.hashCode(), DIMENSIONS)] += 1f;
                }
            }
            double norm = 0;
            for (float x : v) {
                norm += x * x;
            }
            if (norm == 0) {
                v[0] = 1f;
                return v;
            }
            float scale = (float) (1 / Math.sqrt(norm));
            for (int i = 0; i < v.length; i++) {
                v[i] *= scale;
            }
            return v;
        }
    }
}
