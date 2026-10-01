package io.github.snipperxx.docintel.auth;

import io.github.snipperxx.docintel.support.Api;
import io.github.snipperxx.docintel.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends IntegrationTest {

    @Test
    void issuedTokenIdentifiesTheUser() throws Exception {
        Api.User alice = api().newUser("Alice");

        api().get(alice, "/api/auth/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(alice.email()))
                .andExpect(jsonPath("$.displayName").value("Alice"));
    }

    @Test
    void emailIsUniqueIgnoringCase() throws Exception {
        Api.User alice = api().newUser("Alice");

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(Api.json("email", alice.email().toUpperCase(), "displayName", "Other",
                                "password", Api.PASSWORD)))
                .andExpect(status().isConflict());
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameAnswer() throws Exception {
        Api.User alice = api().newUser("Alice");

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(Api.json("email", alice.email(), "password", "wrong-password-123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(Api.json("email", "nobody@example.com", "password", "wrong-password-123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void registrationIsValidated() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(Api.json("email", "not-an-email", "displayName", "X", "password", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void apiRequiresAToken() throws Exception {
        mvc.perform(get("/api/units/mine")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/units/mine").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthAndApiDocsArePublic() throws Exception {
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }
}
