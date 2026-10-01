package io.github.snipperxx.docintel.support;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Small fluent client over MockMvc so tests read as API scenarios rather than request plumbing.
 */
public class Api {

    public static final String PASSWORD = "correct-horse-battery";

    private final MockMvc mvc;

    Api(MockMvc mvc) {
        this.mvc = mvc;
    }

    public User newUser(String name) throws Exception {
        String email = name.toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", email, "displayName", name, "password", PASSWORD)))
                .andExpect(status().isCreated());
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", email, "password", PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new User(email, JsonPath.read(body, "$.accessToken"));
    }

    public long createOrganization(User user, String name) throws Exception {
        return idOf(perform(user, post("/api/organizations").contentType(MediaType.APPLICATION_JSON)
                .content(json("name", name))).andExpect(status().isCreated()));
    }

    public long createUnit(User user, long parentId, String name, String type) throws Exception {
        return idOf(perform(user, post("/api/units/{id}/children", parentId).contentType(MediaType.APPLICATION_JSON)
                .content(json("name", name, "type", type))).andExpect(status().isCreated()));
    }

    public void grant(User admin, long unitId, User member, String role) throws Exception {
        perform(admin, put("/api/units/{id}/members", unitId).contentType(MediaType.APPLICATION_JSON)
                .content(json("email", member.email(), "role", role))).andExpect(status().isOk());
    }

    public ResultActions upload(User user, long projectId, String filename, String content) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, MediaType.TEXT_PLAIN_VALUE,
                content.getBytes(StandardCharsets.UTF_8));
        return perform(user, multipart("/api/projects/{id}/documents", projectId).file(file));
    }

    public UUID uploadDocument(User user, long projectId, String content) throws Exception {
        String body = upload(user, projectId, "notes.txt", content)
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    public ResultActions get(User user, String path, Object... vars) throws Exception {
        return perform(user, MockMvcRequestBuilders.get(path, vars));
    }

    public String documentStatus(User user, UUID documentId) throws Exception {
        String body = get(user, "/api/documents/{id}", documentId)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.status");
    }

    public ResultActions perform(User user, AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
        if (user != null) {
            request.header("Authorization", "Bearer " + user.token());
        }
        return mvc.perform(request);
    }

    private static long idOf(ResultActions result) throws Exception {
        Number id = JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    /**
     * Builds a flat JSON object from key/value pairs; values here never need escaping.
     */
    public static String json(String... keyValues) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < keyValues.length; i += 2) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(keyValues[i]).append("\":\"").append(keyValues[i + 1]).append('"');
        }
        return sb.append('}').toString();
    }

    public record User(String email, String token) {
    }
}
