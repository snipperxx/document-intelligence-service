package io.github.snipperxx.docintel.org;

import io.github.snipperxx.docintel.support.Api;
import io.github.snipperxx.docintel.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Scenario: Alice founds "Acme" with departments Legal and Sales, each with one project.
 */
class AccessControlIntegrationTest extends IntegrationTest {

    private Api api;
    private Api.User alice;
    private long org;
    private long legal;
    private long sales;
    private long contracts;
    private long leads;

    @BeforeEach
    void buildOrganization() throws Exception {
        api = api();
        alice = api.newUser("Alice");
        org = api.createOrganization(alice, "Acme");
        legal = api.createUnit(alice, org, "Legal", "DEPARTMENT");
        sales = api.createUnit(alice, org, "Sales", "DEPARTMENT");
        contracts = api.createUnit(alice, legal, "Contracts", "PROJECT");
        leads = api.createUnit(alice, sales, "Leads", "PROJECT");
    }

    @Test
    void founderIsAdminOfTheWholeTree() throws Exception {
        api.get(alice, "/api/units/{id}", contracts)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").value("ADMIN"))
                .andExpect(jsonPath("$.path").value("/" + org + "/" + legal + "/" + contracts + "/"));
        api.get(alice, "/api/units/{id}/children", org)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void unitTypesMustNestCorrectly() throws Exception {
        api.perform(alice, post("/api/units/{id}/children", org).contentType(MediaType.APPLICATION_JSON)
                        .content(Api.json("name", "Misplaced", "type", "PROJECT")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void roleOnDepartmentIsInheritedByItsProjects() throws Exception {
        Api.User bob = api.newUser("Bob");
        api.grant(alice, legal, bob, "EDITOR");

        api.get(bob, "/api/units/{id}", contracts)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").value("EDITOR"));
        api.uploadDocument(bob, contracts, "Acme Corp employs 120 lawyers.");
    }

    @Test
    void roleDoesNotLeakToSiblingDepartments() throws Exception {
        Api.User bob = api.newUser("Bob");
        api.grant(alice, legal, bob, "EDITOR");

        // 404 rather than 403: Bob must not learn that the Sales project exists.
        api.get(bob, "/api/units/{id}", leads).andExpect(status().isNotFound());
        api.upload(bob, leads, "notes.txt", "Some text.").andExpect(status().isNotFound());
        api.get(bob, "/api/units/{id}", org).andExpect(status().isNotFound());
    }

    @Test
    void viewerCanReadButNotUpload() throws Exception {
        Api.User carol = api.newUser("Carol");
        api.grant(alice, contracts, carol, "VIEWER");
        UUID document = api.uploadDocument(alice, contracts, "Acme Corp owns three buildings.");

        api.get(carol, "/api/documents/{id}", document).andExpect(status().isOk());
        api.get(carol, "/api/projects/{id}/documents", contracts)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1));
        api.upload(carol, contracts, "notes.txt", "Some text.").andExpect(status().isForbidden());
    }

    @Test
    void outsidersSeeNothing() throws Exception {
        Api.User mallory = api.newUser("Mallory");
        UUID document = api.uploadDocument(alice, contracts, "Acme Corp uses a secret supplier.");

        api.get(mallory, "/api/units/{id}", org).andExpect(status().isNotFound());
        api.get(mallory, "/api/documents/{id}", document).andExpect(status().isNotFound());
        api.get(mallory, "/api/documents/{id}/facts", document).andExpect(status().isNotFound());
        api.get(mallory, "/api/units/mine").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void onlyAdminsManageMembers() throws Exception {
        Api.User bob = api.newUser("Bob");
        Api.User carol = api.newUser("Carol");
        api.grant(alice, legal, bob, "EDITOR");

        api.perform(bob, put("/api/units/{id}/members", legal).contentType(MediaType.APPLICATION_JSON)
                        .content(Api.json("email", carol.email(), "role", "VIEWER")))
                .andExpect(status().isForbidden());
        api.perform(bob, post("/api/units/{id}/children", legal).contentType(MediaType.APPLICATION_JSON)
                        .content(Api.json("name", "Side project", "type", "PROJECT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void grantingAgainChangesTheRole() throws Exception {
        Api.User bob = api.newUser("Bob");
        api.grant(alice, legal, bob, "VIEWER");
        api.grant(alice, legal, bob, "ADMIN");

        api.get(alice, "/api/units/{id}/members", legal)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].role").value("ADMIN"));
        api.createUnit(bob, legal, "Bob's project", "PROJECT");
    }
}
