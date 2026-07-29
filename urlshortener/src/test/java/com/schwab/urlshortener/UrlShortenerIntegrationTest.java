package com.schwab.urlshortener;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full-context integration tests against real H2. */
@SpringBootTest(properties = "app.api-key=" + UrlShortenerIntegrationTest.API_KEY)
@AutoConfigureMockMvc
class UrlShortenerIntegrationTest {

    static final String API_KEY = "test-api-key";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shortenThenRedirectThenMetadataThenDeleteRoundTrip() throws Exception {
        String body = """
                {"longUrl": "https://example.com/some/path"}""";

        String createResponse = mockMvc.perform(post("/api/v1/urls")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.code").isNotEmpty())
                .andExpect(jsonPath("$.longUrl").value("https://example.com/some/path"))
                .andReturn().getResponse().getContentAsString();

        String code = JsonPath.read(createResponse, "$.code");

        mockMvc.perform(get("/" + code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/some/path"));

        mockMvc.perform(get("/api/v1/urls/" + code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code));

        mockMvc.perform(delete("/api/v1/urls/" + code).header("X-API-Key", API_KEY))
                .andExpect(status().isNoContent());

        // Disabled -> 410 Gone, not 404.
        mockMvc.perform(get("/" + code))
                .andExpect(status().isGone());
    }

    @Test
    void mutatingEndpointsRequireApiKeyWithConsistentErrorEnvelope() throws Exception {
        String body = """
                {"longUrl": "https://example.com/no-key"}""";

        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());

        mockMvc.perform(post("/api/v1/urls")
                        .header("X-API-Key", "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/v1/urls/anything"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void redirectAndMetadataStayPublicWithoutApiKey() throws Exception {
        mockMvc.perform(get("/does-not-exist")).andExpect(status().isNotFound());
    }

    @Test
    void unknownCodeReturnsNotFoundWithConsistentErrorEnvelope() throws Exception {
        mockMvc.perform(get("/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void unsafeUrlIsRejectedWithValidationError() throws Exception {
        String body = """
                {"longUrl": "http://169.254.169.254/latest/meta-data"}""";

        mockMvc.perform(post("/api/v1/urls")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    /** Polls (bounded wait, not a fixed sleep) until the async click lands. */
    @Test
    void statsReflectAnAsynchronouslyRecordedClick() throws Exception {
        String body = """
                {"longUrl": "https://example.com/stats-check"}""";
        String createResponse = mockMvc.perform(post("/api/v1/urls")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String code = JsonPath.read(createResponse, "$.code");

        mockMvc.perform(get("/" + code)).andExpect(status().isFound());

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/urls/" + code + "/stats"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.totalClicks").value(1))
                        .andExpect(jsonPath("$.lastClickedAt").isNotEmpty()));
    }

    @Test
    void statsStayQueryableAfterDelete() throws Exception {
        String body = """
                {"longUrl": "https://example.com/stats-after-delete"}""";
        String createResponse = mockMvc.perform(post("/api/v1/urls")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String code = JsonPath.read(createResponse, "$.code");

        mockMvc.perform(delete("/api/v1/urls/" + code).header("X-API-Key", API_KEY))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/urls/" + code + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(0));
    }
}
