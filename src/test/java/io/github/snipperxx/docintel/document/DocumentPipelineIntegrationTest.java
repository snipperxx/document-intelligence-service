package io.github.snipperxx.docintel.document;

import com.jayway.jsonpath.JsonPath;
import io.github.snipperxx.docintel.extraction.DocumentProcessor;
import io.github.snipperxx.docintel.support.Api;
import io.github.snipperxx.docintel.support.FakeAiConfiguration;
import io.github.snipperxx.docintel.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End to end through HTTP, PostgreSQL, Kafka and pgvector; only the models are faked.
 */
class DocumentPipelineIntegrationTest extends IntegrationTest {

    private static final String CONTRACT = """
            Northwind Logistics is the carrier for all EU shipments.
            Northwind Logistics has a warehouse in Rotterdam.

            The Rotterdam Warehouse uses solar panels for 40 percent of its power.
            Acme Corp owns the trucks that serve the warehouse.
            """;

    @Autowired
    private DocumentProcessor processor;

    @Autowired
    private FakeAiConfiguration.RecordingChatModel chatModel;

    private Api api;
    private Api.User alice;
    private long project;

    @BeforeEach
    void setUp() throws Exception {
        api = api();
        alice = api.newUser("Alice");
        long org = api.createOrganization(alice, "Acme");
        long department = api.createUnit(alice, org, "Operations", "DEPARTMENT");
        project = api.createUnit(alice, department, "Logistics", "PROJECT");
    }

    @Test
    void uploadIsAcceptedAndProcessedAsynchronously() throws Exception {
        api.upload(alice, project, "contract.txt", CONTRACT)
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/api/documents/")))
                .andExpect(jsonPath("$.status").value("UPLOADED"));
    }

    @Test
    void factsAreAnchoredToTheSourceAndHallucinationsRejected() throws Exception {
        UUID document = api.uploadDocument(alice, project, CONTRACT);
        awaitStatus(document, "EXTRACTED");

        String content = api.get(alice, "/api/documents/{id}/content", document)
                .andReturn().getResponse().getContentAsString();
        String factsJson = api.get(alice, "/api/documents/{id}/facts", document)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> facts = JsonPath.read(factsJson, "$");

        assertThat(facts).hasSize(4);
        for (Map<String, Object> fact : facts) {
            int start = (int) fact.get("startOffset");
            int end = (int) fact.get("endOffset");
            // The stored quote is the original span, even though the fake model lower-cased it.
            assertThat(content.substring(start, end)).isEqualTo(fact.get("quote"));
        }
        assertThat(facts).extracting(f -> f.get("subject"))
                .contains("Northwind Logistics", "Acme Corp")
                .doesNotContain("Moon");

        api.get(alice, "/api/documents/{id}", document)
                .andExpect(jsonPath("$.factCount").value(4))
                .andExpect(jsonPath("$.rejectedCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void duplicateDeliveryDoesNotProcessTwice() throws Exception {
        UUID document = api.uploadDocument(alice, project, CONTRACT);
        awaitStatus(document, "EXTRACTED");

        processor.process(document); // simulates Kafka redelivering the same message

        api.get(alice, "/api/documents/{id}/facts", document).andExpect(jsonPath("$.length()").value(4));
    }

    @Test
    void reprocessingReplacesFactsInsteadOfDuplicating() throws Exception {
        UUID document = api.uploadDocument(alice, project, CONTRACT);
        awaitStatus(document, "EXTRACTED");

        api.perform(alice, post("/api/documents/{id}/reprocess", document)).andExpect(status().isAccepted());
        awaitStatus(document, "EXTRACTED");

        api.get(alice, "/api/documents/{id}/facts", document).andExpect(jsonPath("$.length()").value(4));
    }

    @Test
    void modelFailureMarksDocumentFailed() throws Exception {
        UUID document = api.uploadDocument(alice, project, "This text triggers " + FakeAiConfiguration.FAIL_MARKER);
        awaitStatus(document, "FAILED");

        api.get(alice, "/api/documents/{id}", document)
                .andExpect(jsonPath("$.failureReason").value(org.hamcrest.Matchers.containsString("Simulated")));
    }

    @Test
    void searchOnlyReturnsChunksFromVisibleProjects() throws Exception {
        UUID document = api.uploadDocument(alice, project, CONTRACT);
        awaitStatus(document, "EXTRACTED");

        Api.User outsider = api.newUser("Olga");
        long otherOrg = api.createOrganization(outsider, "Globex");
        long otherDepartment = api.createUnit(outsider, otherOrg, "Ops", "DEPARTMENT");
        long otherProject = api.createUnit(outsider, otherDepartment, "Fleet", "PROJECT");
        UUID otherDocument = api.uploadDocument(outsider, otherProject, "Globex Fleet has electric trucks in Rotterdam.");
        awaitStatus(outsider, otherDocument, "EXTRACTED");

        api.perform(alice, get("/api/search").param("q", "Rotterdam warehouse solar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].documentId").value(document.toString()))
                .andExpect(jsonPath("$[*].documentId").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem(otherDocument.toString()))));
        api.perform(outsider, get("/api/search").param("q", "Rotterdam warehouse solar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].documentId").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem(document.toString()))));
    }

    @Test
    void askAnswersFromRetrievedSources() throws Exception {
        UUID document = api.uploadDocument(alice, project, CONTRACT);
        awaitStatus(document, "EXTRACTED");

        api.perform(alice, post("/api/ask").contentType(MediaType.APPLICATION_JSON)
                        .content(Api.json("question", "Who owns the trucks at the warehouse?")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(FakeAiConfiguration.FAKE_ANSWER))
                .andExpect(jsonPath("$.sources[0].documentId").value(document.toString()));
        assertThat(chatModel.lastPrompt().getContents()).contains("[1]").contains("Acme Corp owns the trucks");
    }

    @Test
    void askWithoutAccessibleDocumentsDoesNotCallTheModel() throws Exception {
        Api.User newcomer = api.newUser("Nora");

        api.perform(newcomer, post("/api/ask").contentType(MediaType.APPLICATION_JSON)
                        .content(Api.json("question", "Who owns the trucks?")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources.length()").value(0));
    }

    @Test
    void deletedDocumentsDisappearFromSearch() throws Exception {
        UUID document = api.uploadDocument(alice, project, CONTRACT);
        awaitStatus(document, "EXTRACTED");

        api.perform(alice, delete("/api/documents/{id}", document)).andExpect(status().isNoContent());

        api.get(alice, "/api/documents/{id}", document).andExpect(status().isNotFound());
        api.perform(alice, get("/api/search").param("q", "Rotterdam warehouse"))
                .andExpect(jsonPath("$[*].documentId").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem(document.toString()))));
    }

    @Test
    void rejectsUnsupportedAndEmptyFiles() throws Exception {
        api.upload(alice, project, "image.png", "not really an image").andExpect(status().isBadRequest());
        api.upload(alice, project, "empty.txt", "").andExpect(status().isBadRequest());
    }

    @Test
    void documentsCanOnlyBeUploadedToProjects() throws Exception {
        long org = api.createOrganization(alice, "Initech");

        api.upload(alice, org, "notes.txt", "Initech has a printer.").andExpect(status().isBadRequest());
    }

    private void awaitStatus(UUID document, String expected) {
        awaitStatus(alice, document, expected);
    }

    private void awaitStatus(Api.User user, UUID document, String expected) {
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200))
                .until(() -> expected.equals(api.documentStatus(user, document)));
    }
}
