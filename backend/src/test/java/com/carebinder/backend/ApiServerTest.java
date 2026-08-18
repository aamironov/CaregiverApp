package com.carebinder.backend;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ApiServerTest {
    @TempDir Path directory;
    private ApiServer server;
    private HttpClient client;
    private String baseUrl;

    @BeforeEach
    void startServer() throws Exception {
        Database database = new Database(directory.resolve("test.db"));
        database.initialize();
        GoogleAuthService googleAuth = new GoogleAuthService(List.of("web-client-id"), credential -> switch (credential) {
            case "alex-google-token" -> new GoogleAuthService.Identity("google-sub-alex", "alex@example.com", true);
            case "new-google-token" -> new GoogleAuthService.Identity("google-sub-new", "new@example.com", false);
            case "third-party-token" -> new GoogleAuthService.Identity("google-sub-third", "third@example.com", false);
            default -> null;
        });
        SourceExtractor extractor = new SourceExtractor() {
            @Override public boolean enabled() { return true; }
            @Override public Source extract(String contentType, String filename, byte[] bytes) {
                return contentType.startsWith("audio/")
                    ? new Source("Call the speech therapist tomorrow. Bring the progress notes.", "BYTEZ_SPEECH", "test-whisper")
                    : new Source("Schedule the follow-up visit. Complete the intake form.", "BYTEZ_DOCUMENT", "test-vision");
            }
        };
        TextTranslator translator = new TextTranslator() {
            @Override public boolean enabled() { return true; }
            @Override public String translate(String text, String targetLanguage) { return "[" + targetLanguage + "] " + text; }
        };
        server = new ApiServer(database, 0, googleAuth, extractor, translator);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.port();
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void stopServer() { server.stop(); }

    @Test
    void completeCarePlanFlowPersistsOnlyAfterExplicitReview() throws Exception {
        String token = register("alex@example.com");
        JsonNode settings = json(request("PATCH", "/v1/settings", token, "{\"language\":\"ru\"}"));
        assertEquals("ru", settings.path("language").asText());
        JsonNode translations = json(request("POST", "/v1/translations", token, "{\"texts\":[\"Call the clinic\",\"Bring the list\"]}"));
        assertEquals("[ru] Call the clinic", translations.path("translations").get(0).asText());
        assertEquals("ru", json(request("GET", "/v1/settings", token, null)).path("language").asText());
        JsonNode recipient = json(request("POST", "/v1/care-recipients", token, "{\"displayName\":\"Maya\",\"relationship\":\"Mother\"}"));
        String recipientId = recipient.path("id").asText();

        JsonNode draft = json(request("POST", "/v1/events/drafts", token, "{\"recipientId\":\"" + recipientId + "\",\"sourceType\":\"TYPED_NOTE\",\"typedNote\":\"Call the clinic next week. Bring the medication list.\"}"));
        assertEquals(2, draft.path("tasks").size());
        assertEquals("note", draft.path("iconKey").asText());
        assertEquals("teal", draft.path("colorKey").asText());
        assertEquals(1, json(request("GET", "/v1/drafts", token, null)).size());
        assertEquals(0, json(request("GET", "/v1/events?recipientId=" + recipientId, token, null)).size());

        JsonNode rejected = request("POST", "/v1/events/confirm", token, "{\"draftId\":\"" + draft.path("draftId").asText() + "\",\"recipientId\":\"" + recipientId + "\",\"items\":[]}");
        assertEquals(400, rejected.path("_httpStatus").asInt());

        String decisions = ApiServer.JSON.writeValueAsString(List.of(
            decision(draft.path("tasks").get(0), "accepted"),
            decision(draft.path("tasks").get(1), "removed")
        ));
        JsonNode event = json(request("POST", "/v1/events/confirm", token, "{\"draftId\":\"" + draft.path("draftId").asText() + "\",\"recipientId\":\"" + recipientId + "\",\"eventSummary\":\"Reviewed visit\",\"familyUpdate\":\"Reviewed update\",\"occurredOn\":\"2026-08-20\",\"timingMode\":\"TIME_RANGE\",\"startsAt\":\"2026-08-20T14:00:00Z\",\"endsAt\":\"2026-08-21T15:00:00Z\",\"recurrenceFrequency\":\"WEEKLY\",\"recurrenceInterval\":1,\"recurrenceUntil\":\"2026-10-01\",\"items\":" + decisions + "}"));
        assertEquals("Reviewed visit", event.path("eventSummary").asText());
        assertEquals("TIME_RANGE", event.path("timingMode").asText());
        assertEquals("2026-08-21T15:00:00Z", event.path("endsAt").asText());
        assertEquals("WEEKLY", event.path("recurrenceFrequency").asText());
        assertEquals(1, event.path("tasks").size());
        assertEquals(0, json(request("GET", "/v1/drafts", token, null)).size());
        assertEquals(1, json(request("GET", "/v1/events?recipientId=" + recipientId, token, null)).size());

        String taskId = event.path("tasks").get(0).path("id").asText();
        JsonNode completed = json(request("PATCH", "/v1/tasks/" + taskId, token, "{\"completed\":true,\"title\":\"Call pediatric clinic\",\"dueDate\":\"2026-08-19\",\"reminderAt\":\"2026-08-20T14:00:00Z\"}"));
        assertTrue(completed.path("completed").asBoolean());
        assertEquals("Call pediatric clinic", completed.path("title").asText());
        assertEquals("2026-08-19", completed.path("dueDate").asText());
        assertEquals("2026-08-20T14:00:00Z", completed.path("reminderAt").asText());

        JsonNode editedEvent = json(request("PATCH", "/v1/events/" + event.path("id").asText(), token, "{\"eventSummary\":\"Weekly therapy\",\"occurredOn\":\"2026-08-21\",\"timingMode\":\"AT_TIME\",\"startsAt\":\"2026-08-21T16:30:00Z\",\"recurrenceFrequency\":\"DAILY\",\"recurrenceUntil\":\"2026-08-31\",\"iconKey\":\"medical\",\"colorKey\":\"violet\"}"));
        assertEquals("Weekly therapy", editedEvent.path("eventSummary").asText());
        assertEquals("AT_TIME", editedEvent.path("timingMode").asText());
        assertEquals("DAILY", editedEvent.path("recurrenceFrequency").asText());
        assertEquals("medical", editedEvent.path("iconKey").asText());
        assertEquals("violet", editedEvent.path("colorKey").asText());

        JsonNode invalidAppearance = request("PATCH", "/v1/events/" + event.path("id").asText(), token, "{\"colorKey\":\"chartreuse\"}");
        assertEquals(400, invalidAppearance.path("_httpStatus").asInt());

        json(request("PATCH", "/v1/tasks/" + taskId, token, "{\"completed\":false}"));
        JsonNode overdueEvent = json(request("PATCH", "/v1/events/" + event.path("id").asText(), token, "{\"occurredOn\":\"2020-01-01\",\"timingMode\":\"ALL_DAY\",\"startsAt\":null,\"endsAt\":null,\"recurrenceFrequency\":\"NONE\",\"recurrenceUntil\":null}"));
        assertTrue(overdueEvent.path("overdue").asBoolean());
    }

    @Test
    void accountAndSourceOwnershipAreEnforcedAndDeletionCascades() throws Exception {
        String alex = register("alex@example.com");
        String sam = register("sam@example.com");
        String recipientId = json(request("POST", "/v1/care-recipients", alex, "{\"displayName\":\"Maya\",\"relationship\":\"Mother\"}")).path("id").asText();
        JsonNode upload = json(request("POST", "/v1/events/uploads", alex, "{\"recipientId\":\"" + recipientId + "\",\"contentType\":\"text/plain\",\"filename\":\"note.txt\"}"));
        json(request("PUT", upload.path("uploadUrl").asText(), alex, "Call the clinic."));
        JsonNode sourceDraft = json(request("POST", "/v1/events/drafts", alex, "{\"recipientId\":\"" + recipientId + "\",\"sourceType\":\"DOCUMENT\",\"assetId\":\"" + upload.path("assetId").asText() + "\"}"));
        assertEquals("Call the clinic.", sourceDraft.path("eventSummary").asText());

        JsonNode audioUpload = json(request("POST", "/v1/events/uploads", alex, "{\"recipientId\":\"" + recipientId + "\",\"contentType\":\"audio/wav\",\"filename\":\"voice.wav\"}"));
        json(request("PUT", audioUpload.path("uploadUrl").asText(), alex, "synthetic audio bytes"));
        JsonNode voiceDraft = json(request("POST", "/v1/events/drafts", alex, "{\"recipientId\":\"" + recipientId + "\",\"sourceType\":\"VOICE_NOTE\",\"assetId\":\"" + audioUpload.path("assetId").asText() + "\"}"));
        assertEquals(2, voiceDraft.path("tasks").size());
        assertEquals("BYTEZ_SPEECH", voiceDraft.path("sourceExtraction").path("kind").asText());
        assertEquals("test-whisper", voiceDraft.path("sourceExtraction").path("model").asText());

        JsonNode forbidden = request("POST", "/v1/events/drafts", sam, "{\"recipientId\":\"" + recipientId + "\",\"sourceType\":\"DOCUMENT\",\"assetId\":\"" + upload.path("assetId").asText() + "\"}");
        assertEquals(403, forbidden.path("_httpStatus").asInt());

        assertEquals(202, request("DELETE", "/v1/account", alex, "{\"confirmation\":\"DELETE\"}").path("_httpStatus").asInt());
        assertEquals(401, request("GET", "/v1/care-recipients/me", alex, null).path("_httpStatus").asInt());
    }

    @Test
    void webApplicationAndHealthCheckAreServed() throws Exception {
        HttpResponse<String> page = raw("GET", "/", null, null);
        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("CareBinder"));
        assertFalse(page.body().contains("<script>"));
        assertTrue(page.headers().firstValue("Content-Security-Policy").orElse("").contains("https://accounts.google.com/gsi/client"));
        assertEquals("same-origin-allow-popups", page.headers().firstValue("Cross-Origin-Opener-Policy").orElse(""));
        HttpResponse<String> worker = raw("GET", "/sw.js", null, null);
        assertEquals(200, worker.statusCode());
        assertTrue(worker.body().contains("carebinder-shell-v1"));
        assertEquals("/", worker.headers().firstValue("Service-Worker-Allowed").orElse(""));
        assertEquals("ok", json(request("GET", "/v1/health", null, null)).path("status").asText());
        JsonNode config = json(request("GET", "/v1/auth/config", null, null));
        assertTrue(config.path("googleEnabled").asBoolean());
        assertTrue(config.path("bytezEnabled").asBoolean());
        assertTrue(config.path("translationEnabled").asBoolean());
        assertEquals("web-client-id", config.path("googleClientId").asText());
    }

    @Test
    void verifiedGoogleIdentityCreatesAndLinksAccounts() throws Exception {
        String passwordToken = register("alex@example.com");
        register("third@example.com");
        json(request("POST", "/v1/care-recipients", passwordToken, "{\"displayName\":\"Maya\",\"relationship\":\"Mother\"}"));

        String linkedToken = json(request("POST", "/v1/auth/google", null, "{\"credential\":\"alex-google-token\"}"))
            .path("accessToken").asText();
        assertEquals("Maya", json(request("GET", "/v1/care-recipients/me", linkedToken, null)).path("displayName").asText());

        String newToken = json(request("POST", "/v1/auth/google", null, "{\"credential\":\"new-google-token\"}"))
            .path("accessToken").asText();
        assertEquals(404, request("GET", "/v1/care-recipients/me", newToken, null).path("_httpStatus").asInt());
        assertEquals(401, request("POST", "/v1/auth/google", null, "{\"credential\":\"forged\"}").path("_httpStatus").asInt());
        JsonNode linkRequired = request("POST", "/v1/auth/google", null, "{\"credential\":\"third-party-token\"}");
        assertEquals(409, linkRequired.path("_httpStatus").asInt());
        assertEquals("ACCOUNT_LINK_REQUIRED", linkRequired.path("code").asText());
    }

    private java.util.Map<String, String> decision(JsonNode task, String decision) {
        return java.util.Map.of("draftItemId", task.path("id").asText(), "decision", decision, "title", task.path("title").asText());
    }

    private String register(String email) throws Exception {
        return json(request("POST", "/v1/auth/register", null, "{\"email\":\"" + email + "\",\"password\":\"test-password\"}")).path("accessToken").asText();
    }

    private JsonNode request(String method, String path, String token, String body) throws Exception {
        HttpResponse<String> response = raw(method, path, token, body);
        JsonNode parsed = ApiServer.JSON.readTree(response.body().isBlank() ? "{}" : response.body());
        if (response.statusCode() >= 400 || response.statusCode() == 202) ((com.fasterxml.jackson.databind.node.ObjectNode) parsed).put("_httpStatus", response.statusCode());
        return parsed;
    }

    private JsonNode json(JsonNode node) {
        assertFalse(node.has("_httpStatus"), () -> "Unexpected error: " + node);
        return node;
    }

    private HttpResponse<String> raw(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
