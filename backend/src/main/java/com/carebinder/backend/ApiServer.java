package com.carebinder.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;

public final class ApiServer {
    static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_JSON_BYTES = 1_000_000;
    private static final int MAX_UPLOAD_BYTES = 10_000_000;
    private static final Set<String> DECISIONS = Set.of("accepted", "edited", "removed");
    private static final Set<String> TIMING_MODES = Set.of("ALL_DAY", "AT_TIME", "TIME_RANGE");
    private static final Set<String> RECURRENCE_FREQUENCIES = Set.of("NONE", "DAILY", "WEEKLY", "MONTHLY");
    private static final Set<String> ICON_KEYS = Set.of("note", "document", "voice", "medical", "calendar", "meal", "sleep", "activity");
    private static final Set<String> COLOR_KEYS = Set.of("slate", "gray", "red", "orange", "amber", "yellow", "lime", "green", "emerald", "teal", "cyan", "sky", "blue", "indigo", "violet", "pink");
    private static final Set<String> LANGUAGES = Set.of("en", "ru", "es");

    private final Database database;
    private final HttpServer server;
    private final GoogleAuthService googleAuth;
    private final SourceExtractor sourceExtractor;
    private final TextTranslator translator;

    public ApiServer(Database database, int port) throws IOException {
        this(database, port, GoogleAuthService.disabled(), SourceExtractor.disabled(), TextTranslator.disabled());
    }

    ApiServer(Database database, int port, GoogleAuthService googleAuth) throws IOException {
        this(database, port, googleAuth, SourceExtractor.disabled(), TextTranslator.disabled());
    }

    ApiServer(Database database, int port, GoogleAuthService googleAuth, SourceExtractor sourceExtractor) throws IOException {
        this(database, port, googleAuth, sourceExtractor, TextTranslator.disabled());
    }

    ApiServer(Database database, int port, GoogleAuthService googleAuth, SourceExtractor sourceExtractor, TextTranslator translator) throws IOException {
        this.database = database;
        this.googleAuth = googleAuth;
        this.sourceExtractor = sourceExtractor;
        this.translator = translator;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        this.server.createContext("/", this::handle);
    }

    public void start() { server.start(); }
    public void stop() { server.stop(0); }
    public int port() { return server.getAddress().getPort(); }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            addCommonHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            if (!path.startsWith("/v1/")) {
                serveWeb(exchange, path);
                return;
            }
            if (method.equals("GET") && path.equals("/v1/health")) {
                sendJson(exchange, 200, JSON.createObjectNode().put("status", "ok"));
                return;
            }
            if (method.equals("GET") && path.equals("/v1/auth/config")) {
                ObjectNode config = JSON.createObjectNode().put("googleEnabled", googleAuth.enabled()).put("bytezEnabled", sourceExtractor.enabled()).put("translationEnabled", translator.enabled());
                if (googleAuth.enabled()) config.put("googleClientId", googleAuth.webClientId());
                sendJson(exchange, 200, config);
                return;
            }
            if (method.equals("POST") && path.equals("/v1/auth/register")) {
                register(exchange);
                return;
            }
            if (method.equals("POST") && path.equals("/v1/auth/login")) {
                login(exchange);
                return;
            }
            if (method.equals("POST") && path.equals("/v1/auth/google")) {
                googleLogin(exchange);
                return;
            }

            String userId = authenticatedUser(exchange);
            if (userId == null) throw new ApiError(401, "UNAUTHENTICATED", "Sign in to continue.");

            if (method.equals("POST") && path.equals("/v1/auth/logout")) logout(exchange);
            else if (method.equals("GET") && path.equals("/v1/settings")) getSettings(exchange, userId);
            else if (method.equals("PATCH") && path.equals("/v1/settings")) updateSettings(exchange, userId);
            else if (method.equals("POST") && path.equals("/v1/translations")) translateTexts(exchange, userId);
            else if (method.equals("POST") && path.equals("/v1/care-recipients")) createRecipient(exchange, userId);
            else if (method.equals("GET") && path.equals("/v1/care-recipients/me")) getRecipient(exchange, userId);
            else if (method.equals("PATCH") && path.equals("/v1/care-recipients/me")) updateRecipient(exchange, userId);
            else if (method.equals("POST") && path.equals("/v1/events/uploads")) createUpload(exchange, userId);
            else if (method.equals("PUT") && path.startsWith("/v1/uploads/")) putUpload(exchange, userId, tail(path));
            else if (method.equals("GET") && path.startsWith("/v1/assets/")) getAsset(exchange, userId, tail(path));
            else if (method.equals("POST") && path.equals("/v1/events/drafts")) createDraft(exchange, userId);
            else if (method.equals("GET") && path.equals("/v1/drafts")) listDrafts(exchange, userId);
            else if (method.equals("PUT") && path.startsWith("/v1/drafts/")) saveDraft(exchange, userId, tail(path));
            else if (method.equals("DELETE") && path.startsWith("/v1/drafts/")) deleteDraft(exchange, userId, tail(path));
            else if (method.equals("POST") && path.equals("/v1/events/confirm")) confirmDraft(exchange, userId);
            else if (method.equals("GET") && path.equals("/v1/events")) listEvents(exchange, userId);
            else if (method.equals("GET") && path.startsWith("/v1/events/")) sendJson(exchange, 200, loadEvent(userId, tail(path)));
            else if (method.equals("PATCH") && path.startsWith("/v1/events/")) updateEvent(exchange, userId, tail(path));
            else if (method.equals("PATCH") && path.startsWith("/v1/tasks/")) updateTask(exchange, userId, tail(path));
            else if (method.equals("POST") && path.equals("/v1/exports")) createExport(exchange, userId);
            else if (method.equals("GET") && path.startsWith("/v1/exports/")) downloadExport(exchange, userId, tail(path));
            else if (method.equals("DELETE") && path.equals("/v1/account")) deleteAccount(exchange, userId);
            else throw new ApiError(404, "NOT_FOUND", "Route not found.");
        } catch (ApiError error) {
            sendError(exchange, error.status, error.code, error.getMessage(), error.retryable);
        } catch (SQLException error) {
            error.printStackTrace(System.err);
            sendError(exchange, 500, "STORAGE_ERROR", "CareBinder could not save that change. Try again.", true);
        } catch (Exception error) {
            error.printStackTrace(System.err);
            sendError(exchange, 400, "VALIDATION_ERROR", "Request could not be processed.", false);
        } finally {
            exchange.close();
        }
    }

    private void register(HttpExchange exchange) throws Exception {
        JsonNode body = readJson(exchange);
        String email = requiredText(body, "email").toLowerCase(Locale.ROOT);
        String password = requiredText(body, "password");
        if (!email.contains("@") || email.length() > 254) throw new ApiError(400, "VALIDATION_ERROR", "Enter a valid email address.");
        if (password.length() < 8 || password.length() > 128) throw new ApiError(400, "VALIDATION_ERROR", "Password must be 8–128 characters.");
        String userId = UUID.randomUUID().toString();
        String salt = Security.randomSalt();
        String now = Instant.now().toString();
        try (Connection connection = database.open()) {
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO users(id,email,password_hash,password_salt,created_at) VALUES(?,?,?,?,?)")) {
                statement.setString(1, userId);
                statement.setString(2, email);
                statement.setString(3, Security.passwordHash(password.toCharArray(), salt));
                statement.setString(4, salt);
                statement.setString(5, now);
                statement.executeUpdate();
            } catch (SQLException error) {
                if (error.getMessage().contains("UNIQUE")) throw new ApiError(409, "ACCOUNT_EXISTS", "An account already exists for this email.");
                throw error;
            }
            sendSession(exchange, connection, userId, email);
        }
    }

    private void login(HttpExchange exchange) throws Exception {
        JsonNode body = readJson(exchange);
        String email = requiredText(body, "email").toLowerCase(Locale.ROOT);
        String password = requiredText(body, "password");
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement("SELECT id,password_hash,password_salt FROM users WHERE email=?")) {
            statement.setString(1, email);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw invalidCredentials();
                String candidate = Security.passwordHash(password.toCharArray(), result.getString("password_salt"));
                if (!Security.constantTimeEquals(candidate, result.getString("password_hash"))) throw invalidCredentials();
                sendSession(exchange, connection, result.getString("id"), email);
            }
        }
    }

    private ApiError invalidCredentials() {
        return new ApiError(401, "INVALID_CREDENTIALS", "Email or password is incorrect.");
    }

    private void googleLogin(HttpExchange exchange) throws Exception {
        if (!googleAuth.enabled()) throw new ApiError(503, "GOOGLE_AUTH_DISABLED", "Google sign-in is not configured.");
        GoogleAuthService.Identity identity = googleAuth.verify(requiredText(readJson(exchange), "credential"));
        if (identity == null) throw new ApiError(401, "INVALID_GOOGLE_CREDENTIAL", "Google sign-in could not be verified.");

        try (Connection connection = database.open()) {
            connection.setAutoCommit(false);
            try {
                String userId = null;
                String email = identity.email();
                try (PreparedStatement statement = connection.prepareStatement("SELECT id,email FROM users WHERE google_sub=?")) {
                    statement.setString(1, identity.subject());
                    try (ResultSet result = statement.executeQuery()) {
                        if (result.next()) {
                            userId = result.getString("id");
                            email = result.getString("email");
                        }
                    }
                }
                if (userId == null) {
                    try (PreparedStatement statement = connection.prepareStatement("SELECT id,google_sub FROM users WHERE email=?")) {
                        statement.setString(1, email);
                        try (ResultSet result = statement.executeQuery()) {
                            if (result.next()) {
                                if (!identity.authoritativeEmail() && result.getString("google_sub") == null) {
                                    throw new ApiError(409, "ACCOUNT_LINK_REQUIRED", "This email already has a password account. Continue with password sign-in.");
                                }
                                if (result.getString("google_sub") != null && !identity.subject().equals(result.getString("google_sub"))) {
                                    throw new ApiError(409, "ACCOUNT_LINK_CONFLICT", "This email is linked to another Google account.");
                                }
                                userId = result.getString("id");
                            }
                        }
                    }
                    if (userId == null) {
                        userId = UUID.randomUUID().toString();
                        String salt = Security.randomSalt();
                        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO users(id,email,password_hash,password_salt,created_at,google_sub) VALUES(?,?,?,?,?,?)")) {
                            statement.setString(1, userId);
                            statement.setString(2, email);
                            statement.setString(3, Security.passwordHash(Security.randomToken().toCharArray(), salt));
                            statement.setString(4, salt);
                            statement.setString(5, Instant.now().toString());
                            statement.setString(6, identity.subject());
                            statement.executeUpdate();
                        }
                    } else {
                        try (PreparedStatement statement = connection.prepareStatement("UPDATE users SET google_sub=? WHERE id=?")) {
                            statement.setString(1, identity.subject());
                            statement.setString(2, userId);
                            statement.executeUpdate();
                        }
                    }
                }
                connection.commit();
                connection.setAutoCommit(true);
                sendSession(exchange, connection, userId, email);
            } catch (Exception error) {
                if (!connection.getAutoCommit()) connection.rollback();
                throw error;
            } finally {
                if (!connection.getAutoCommit()) connection.setAutoCommit(true);
            }
        }
    }

    private void sendSession(HttpExchange exchange, Connection connection, String userId, String email) throws Exception {
        String token = Security.randomToken();
        Instant expiresAt = Instant.now().plus(30, ChronoUnit.DAYS);
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO sessions(token_hash,user_id,expires_at,created_at) VALUES(?,?,?,?)")) {
            statement.setString(1, Security.tokenHash(token));
            statement.setString(2, userId);
            statement.setString(3, expiresAt.toString());
            statement.setString(4, Instant.now().toString());
            statement.executeUpdate();
        }
        ObjectNode response = JSON.createObjectNode();
        response.put("accessToken", token);
        response.put("expiresAt", expiresAt.toString());
        response.putObject("user").put("id", userId).put("email", email).put("preferredLanguage", preferredLanguage(connection, userId));
        sendJson(exchange, 200, response);
    }

    private String preferredLanguage(Connection connection, String userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT preferred_language FROM users WHERE id=?")) {
            statement.setString(1, userId);
            try (ResultSet result = statement.executeQuery()) { return result.next() ? result.getString(1) : "en"; }
        }
    }

    private void logout(HttpExchange exchange) throws Exception {
        String token = bearer(exchange);
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement("DELETE FROM sessions WHERE token_hash=?")) {
            statement.setString(1, Security.tokenHash(token));
            statement.executeUpdate();
        }
        exchange.sendResponseHeaders(204, -1);
    }

    private void getSettings(HttpExchange exchange, String userId) throws Exception {
        try (Connection connection = database.open()) {
            sendJson(exchange, 200, JSON.createObjectNode().put("language", preferredLanguage(connection, userId)).put("translationEnabled", translator.enabled()));
        }
    }

    private void updateSettings(HttpExchange exchange, String userId) throws Exception {
        String language = requiredText(readJson(exchange), "language").toLowerCase(Locale.ROOT);
        if (!LANGUAGES.contains(language)) throw new ApiError(400, "VALIDATION_ERROR", "Choose English, Russian, or Spanish.");
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement("UPDATE users SET preferred_language=? WHERE id=?")) {
            statement.setString(1, language); statement.setString(2, userId); statement.executeUpdate();
        }
        sendJson(exchange, 200, JSON.createObjectNode().put("language", language).put("translationEnabled", translator.enabled()));
    }

    private void translateTexts(HttpExchange exchange, String userId) throws Exception {
        JsonNode body = readJson(exchange);
        JsonNode texts = body.path("texts");
        if (!texts.isArray() || texts.size() > 40) throw new ApiError(400, "VALIDATION_ERROR", "Translate up to 40 text items at a time.");
        String language;
        try (Connection connection = database.open()) { language = preferredLanguage(connection, userId); }
        ArrayNode output = JSON.createArrayNode();
        for (JsonNode item : texts) {
            String original = item.asText();
            if (original.length() > 5_000) throw new ApiError(413, "PAYLOAD_TOO_LARGE", "A text item is too long to translate.");
            try {
                output.add(translator.enabled() ? translator.translate(original, language) : original);
            } catch (BytezTranslator.TranslationException error) {
                throw new ApiError(error.retryable ? 503 : 422, "TRANSLATION_FAILED", error.getMessage(), error.retryable);
            }
        }
        ObjectNode response = JSON.createObjectNode().put("language", language).put("translationEnabled", translator.enabled());
        response.set("translations", output);
        sendJson(exchange, 200, response);
    }

    private void createRecipient(HttpExchange exchange, String userId) throws Exception {
        JsonNode body = readJson(exchange);
        String name = requiredText(body, "displayName");
        String relationship = requiredText(body, "relationship");
        String id = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement("INSERT INTO recipients(id,user_id,display_name,relationship,created_at,updated_at) VALUES(?,?,?,?,?,?)")) {
            statement.setString(1, id); statement.setString(2, userId); statement.setString(3, name); statement.setString(4, relationship); statement.setString(5, now); statement.setString(6, now);
            try { statement.executeUpdate(); }
            catch (SQLException error) {
                if (error.getMessage().contains("UNIQUE")) throw new ApiError(409, "PROFILE_EXISTS", "This MVP supports one care profile per account.");
                throw error;
            }
        }
        sendJson(exchange, 201, recipientJson(id, name, relationship));
    }

    private void getRecipient(HttpExchange exchange, String userId) throws Exception {
        ObjectNode recipient = findRecipient(userId);
        if (recipient == null) throw new ApiError(404, "PROFILE_NOT_FOUND", "Create a care profile to continue.");
        sendJson(exchange, 200, recipient);
    }

    private void updateRecipient(HttpExchange exchange, String userId) throws Exception {
        JsonNode body = readJson(exchange);
        String name = requiredText(body, "displayName");
        String relationship = requiredText(body, "relationship");
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement("UPDATE recipients SET display_name=?,relationship=?,updated_at=? WHERE user_id=?")) {
            statement.setString(1, name); statement.setString(2, relationship); statement.setString(3, Instant.now().toString()); statement.setString(4, userId);
            if (statement.executeUpdate() == 0) throw new ApiError(404, "PROFILE_NOT_FOUND", "Care profile not found.");
        }
        sendJson(exchange, 200, findRecipient(userId));
    }

    private ObjectNode findRecipient(String userId) throws Exception {
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement("SELECT id,display_name,relationship FROM recipients WHERE user_id=?")) {
            statement.setString(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? recipientJson(result.getString("id"), result.getString("display_name"), result.getString("relationship")) : null;
            }
        }
    }

    private ObjectNode recipientJson(String id, String name, String relationship) {
        return JSON.createObjectNode().put("id", id).put("displayName", name).put("relationship", relationship);
    }

    private void createUpload(HttpExchange exchange, String userId) throws Exception {
        JsonNode body = readJson(exchange);
        String recipientId = requiredText(body, "recipientId");
        requireRecipient(userId, recipientId);
        String contentType = requiredText(body, "contentType");
        String filename = requiredText(body, "filename");
        String id = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(10, ChronoUnit.MINUTES);
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement("INSERT INTO assets(id,user_id,recipient_id,content_type,filename,expires_at,created_at) VALUES(?,?,?,?,?,?,?)")) {
            statement.setString(1, id); statement.setString(2, userId); statement.setString(3, recipientId); statement.setString(4, contentType); statement.setString(5, filename); statement.setString(6, expiresAt.toString()); statement.setString(7, Instant.now().toString()); statement.executeUpdate();
        }
        sendJson(exchange, 201, JSON.createObjectNode().put("assetId", id).put("uploadUrl", "/v1/uploads/" + id).put("expiresAt", expiresAt.toString()));
    }

    private void putUpload(HttpExchange exchange, String userId, String assetId) throws Exception {
        byte[] bytes = readBytes(exchange.getRequestBody(), MAX_UPLOAD_BYTES);
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement("UPDATE assets SET bytes=? WHERE id=? AND user_id=? AND expires_at>?")) {
            statement.setBytes(1, bytes); statement.setString(2, assetId); statement.setString(3, userId); statement.setString(4, Instant.now().toString());
            if (statement.executeUpdate() == 0) throw new ApiError(404, "SOURCE_UNREADABLE", "Upload link is unavailable.");
        }
        sendJson(exchange, 200, JSON.createObjectNode().put("assetId", assetId).put("uploaded", true));
    }

    private void getAsset(HttpExchange exchange, String userId, String assetId) throws Exception {
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement("SELECT content_type,filename,bytes FROM assets WHERE id=? AND user_id=?")) {
            statement.setString(1, assetId); statement.setString(2, userId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || result.getBytes("bytes") == null) throw new ApiError(404, "NOT_FOUND", "Source not found.");
                byte[] bytes = result.getBytes("bytes");
                exchange.getResponseHeaders().set("Content-Type", result.getString("content_type"));
                exchange.getResponseHeaders().set("Content-Disposition", "inline; filename=\"" + safeFilename(result.getString("filename")) + "\"");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        }
    }

    private void createDraft(HttpExchange exchange, String userId) throws Exception {
        JsonNode body = readJson(exchange);
        String recipientId = requiredText(body, "recipientId");
        requireRecipient(userId, recipientId);
        String sourceType = requiredText(body, "sourceType");
        String assetId = optionalText(body, "assetId");
        String caregiverNote = optionalText(body, "typedNote");
        String source = caregiverNote;
        SourceExtractor.Source extraction = null;
        if (assetId != null) {
            Asset asset = requireAsset(userId, recipientId, assetId);
            if (asset.bytes == null || asset.bytes.length == 0) throw new ApiError(422, "SOURCE_UNREADABLE", "Upload the source before creating a draft.");
            if (asset.contentType.startsWith("text/")) {
                String uploadedText = new String(asset.bytes, StandardCharsets.UTF_8).strip();
                if (!uploadedText.isBlank()) extraction = new SourceExtractor.Source(uploadedText, "UPLOADED_TEXT", "local");
            } else if (sourceExtractor.enabled()) {
                try {
                    extraction = sourceExtractor.extract(asset.contentType, asset.filename, asset.bytes);
                } catch (BytezSourceExtractor.ExtractionException error) {
                    throw new ApiError(error.retryable ? 503 : 422, "SOURCE_EXTRACTION_FAILED", error.getMessage(), error.retryable);
                }
            } else if (caregiverNote == null || caregiverNote.isBlank()) {
                throw new ApiError(503, "BYTEZ_NOT_CONFIGURED", "Automatic document and speech extraction is not configured. Set BYTEZ_API_KEY on the backend or add a transcript.");
            }
            if (extraction != null) {
                source = extraction.text();
                if (caregiverNote != null && !caregiverNote.isBlank()) source += "\n\nCaregiver note: " + caregiverNote.strip();
            }
        }
        if (source == null || source.isBlank()) throw new ApiError(422, "SOURCE_UNREADABLE", "Add a note, document, or voice recording so CareBinder can create a reviewable draft.");
        String draftId = UUID.randomUUID().toString();
        ObjectNode draft = DraftGenerator.generate(draftId, recipientId, sourceType, assetId, source);
        if (extraction != null) {
            ObjectNode metadata = draft.putObject("sourceExtraction");
            metadata.put("kind", extraction.kind()).put("model", extraction.model()).put("requiresReview", true);
        }
        copyIfPresent(body, draft, "occurredOn");
        copySchedule(body, draft);
        copyAppearance(body, draft);
        validateSchedule(draft);
        validateAppearance(draft);
        String now = Instant.now().toString();
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement("INSERT INTO drafts(id,user_id,recipient_id,source_type,asset_id,payload_json,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?)")) {
            statement.setString(1, draftId); statement.setString(2, userId); statement.setString(3, recipientId); statement.setString(4, sourceType); statement.setString(5, assetId); statement.setString(6, JSON.writeValueAsString(draft)); statement.setString(7, now); statement.setString(8, now); statement.executeUpdate();
        }
        sendJson(exchange, 201, draft);
    }

    private void listDrafts(HttpExchange exchange, String userId) throws Exception {
        ArrayNode drafts = JSON.createArrayNode();
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement("SELECT payload_json FROM drafts WHERE user_id=? ORDER BY updated_at DESC")) {
            statement.setString(1, userId);
            try (ResultSet result = statement.executeQuery()) { while (result.next()) drafts.add(JSON.readTree(result.getString(1))); }
        }
        sendJson(exchange, 200, drafts);
    }

    private void saveDraft(HttpExchange exchange, String userId, String draftId) throws Exception {
        JsonNode edited = readJson(exchange);
        ObjectNode original = requireDraft(userId, draftId);
        if (edited.has("tasks") || edited.has("medicationItems")) {
            Set<String> originalIds = draftItemIds(original);
            Set<String> editedIds = draftItemIds(edited);
            if (!originalIds.equals(editedIds)) throw new ApiError(400, "VALIDATION_ERROR", "Generated draft items cannot be added or omitted. Mark an item removed instead.");
        }
        ObjectNode merged = original.deepCopy();
        copyIfPresent(edited, merged, "eventSummary");
        copyIfPresent(edited, merged, "familyUpdate");
        copyIfPresent(edited, merged, "occurredOn");
        copySchedule(edited, merged);
        copyIfPresent(edited, merged, "questionsForClinician");
        copyIfPresent(edited, merged, "tasks");
        copyIfPresent(edited, merged, "medicationItems");
        copyAppearance(edited, merged);
        validateSchedule(merged);
        validateAppearance(merged);
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement("UPDATE drafts SET payload_json=?,updated_at=? WHERE id=? AND user_id=?")) {
            statement.setString(1, JSON.writeValueAsString(merged)); statement.setString(2, Instant.now().toString()); statement.setString(3, draftId); statement.setString(4, userId);
            if (statement.executeUpdate() == 0) throw new ApiError(404, "DRAFT_EXPIRED", "Draft not found.");
        }
        sendJson(exchange, 200, merged);
    }

    private void deleteDraft(HttpExchange exchange, String userId, String draftId) throws Exception {
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement("DELETE FROM drafts WHERE id=? AND user_id=?")) {
            statement.setString(1, draftId); statement.setString(2, userId); statement.executeUpdate();
        }
        exchange.sendResponseHeaders(204, -1);
    }

    private void confirmDraft(HttpExchange exchange, String userId) throws Exception {
        JsonNode body = readJson(exchange);
        String draftId = requiredText(body, "draftId");
        ObjectNode draft = requireDraft(userId, draftId);
        String recipientId = draft.path("recipientId").asText();
        if (!recipientId.equals(requiredText(body, "recipientId"))) throw new ApiError(403, "FORBIDDEN", "Draft does not belong to this care profile.");

        Map<String, JsonNode> generated = new HashMap<>();
        draft.withArray("tasks").forEach(item -> generated.put(item.path("id").asText(), item));
        draft.withArray("medicationItems").forEach(item -> generated.put(item.path("id").asText(), item));
        Map<String, JsonNode> decisions = new HashMap<>();
        JsonNode items = body.path("items");
        if (!items.isArray()) throw confirmationError();
        for (JsonNode item : items) {
            String id = item.path("draftItemId").asText();
            String decision = item.path("decision").asText();
            if (!generated.containsKey(id) || !DECISIONS.contains(decision) || decisions.put(id, item) != null) throw confirmationError();
        }
        if (!decisions.keySet().equals(generated.keySet())) throw confirmationError();

        String eventId = UUID.randomUUID().toString();
        String summary = textOr(body, "eventSummary", draft.path("eventSummary").asText());
        String familyUpdate = textOr(body, "familyUpdate", draft.path("familyUpdate").asText());
        String occurredOn = textOr(body, "occurredOn", draft.path("occurredOn").asText(LocalDate.now().toString()));
        ObjectNode schedule = draft.deepCopy();
        schedule.put("occurredOn", occurredOn);
        copySchedule(body, schedule);
        validateSchedule(schedule);
        copyAppearance(body, schedule);
        validateAppearance(schedule);
        JsonNode questions = body.has("questionsForClinician") ? body.get("questionsForClinician") : draft.path("questionsForClinician");
        String now = Instant.now().toString();

        try (Connection connection = database.open()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement("INSERT INTO events(id,user_id,recipient_id,source_type,asset_id,event_summary,family_update,questions_json,occurred_on,timing_mode,starts_at,ends_at,recurrence_frequency,recurrence_interval,recurrence_until,icon_key,color_key,confirmed_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                    statement.setString(1, eventId); statement.setString(2, userId); statement.setString(3, recipientId); statement.setString(4, draft.path("sourceType").asText()); statement.setString(5, optionalText(draft, "assetId")); statement.setString(6, summary); statement.setString(7, familyUpdate); statement.setString(8, JSON.writeValueAsString(questions)); statement.setString(9, occurredOn);
                    statement.setString(10, schedule.path("timingMode").asText("ALL_DAY"));
                    statement.setString(11, nullableText(schedule, "startsAt", null));
                    statement.setString(12, nullableText(schedule, "endsAt", null));
                    statement.setString(13, schedule.path("recurrenceFrequency").asText("NONE"));
                    statement.setInt(14, schedule.path("recurrenceInterval").asInt(1));
                    statement.setString(15, nullableText(schedule, "recurrenceUntil", null));
                    statement.setString(16, schedule.path("iconKey").asText("note"));
                    statement.setString(17, schedule.path("colorKey").asText("teal"));
                    statement.setString(18, now);
                    statement.executeUpdate();
                }
                for (Map.Entry<String, JsonNode> entry : generated.entrySet()) {
                    JsonNode decisionNode = decisions.get(entry.getKey());
                    if (decisionNode.path("decision").asText().equals("removed")) continue;
                    JsonNode sourceItem = entry.getValue();
                    try (PreparedStatement statement = connection.prepareStatement("INSERT INTO tasks(id,event_id,kind,title,due_date,reminder_at,source_text,decision,completed) VALUES(?,?,?,?,?,?,?,?,0)")) {
                        statement.setString(1, UUID.randomUUID().toString()); statement.setString(2, eventId); statement.setString(3, sourceItem.path("kind").asText("TASK")); statement.setString(4, textOr(decisionNode, "title", sourceItem.path("title").asText())); statement.setString(5, nullableText(decisionNode, "dueDate", sourceItem.path("dueDate").textValue())); statement.setString(6, nullableText(decisionNode, "reminderAt", null)); statement.setString(7, sourceItem.path("sourceText").asText()); statement.setString(8, decisionNode.path("decision").asText()); statement.executeUpdate();
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM drafts WHERE id=? AND user_id=?")) { statement.setString(1, draftId); statement.setString(2, userId); statement.executeUpdate(); }
                connection.commit();
            } catch (Exception error) {
                connection.rollback();
                throw error;
            } finally { connection.setAutoCommit(true); }
        }
        sendJson(exchange, 201, loadEvent(userId, eventId));
    }

    private ApiError confirmationError() {
        return new ApiError(400, "VALIDATION_ERROR", "Every generated task and medication item needs one caregiver decision.");
    }

    private void listEvents(HttpExchange exchange, String userId) throws Exception {
        String recipientId = queryParameter(exchange, "recipientId");
        if (recipientId != null) requireRecipient(userId, recipientId);
        ArrayNode events = JSON.createArrayNode();
        String sql = recipientId == null ? "SELECT id FROM events WHERE user_id=? ORDER BY occurred_on DESC, confirmed_at DESC" : "SELECT id FROM events WHERE user_id=? AND recipient_id=? ORDER BY occurred_on DESC, confirmed_at DESC";
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId); if (recipientId != null) statement.setString(2, recipientId);
            try (ResultSet result = statement.executeQuery()) { while (result.next()) events.add(loadEvent(userId, result.getString(1))); }
        }
        sendJson(exchange, 200, events);
    }

    private ObjectNode loadEvent(String userId, String eventId) throws Exception {
        ObjectNode event;
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement("SELECT * FROM events WHERE id=? AND user_id=?")) {
            statement.setString(1, eventId); statement.setString(2, userId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new ApiError(404, "NOT_FOUND", "Event not found.");
                event = JSON.createObjectNode();
                event.put("id", result.getString("id")); event.put("recipientId", result.getString("recipient_id")); event.put("sourceType", result.getString("source_type"));
                String assetId = result.getString("asset_id"); if (assetId != null) event.put("assetId", assetId);
                event.put("eventSummary", result.getString("event_summary")); event.put("familyUpdate", result.getString("family_update")); event.set("questionsForClinician", JSON.readTree(result.getString("questions_json"))); event.put("occurredOn", result.getString("occurred_on"));
                event.put("timingMode", result.getString("timing_mode")); putNullable(event, "startsAt", result.getString("starts_at")); putNullable(event, "endsAt", result.getString("ends_at")); event.put("recurrenceFrequency", result.getString("recurrence_frequency")); event.put("recurrenceInterval", result.getInt("recurrence_interval")); putNullable(event, "recurrenceUntil", result.getString("recurrence_until")); event.put("confirmedAt", result.getString("confirmed_at"));
                event.put("iconKey", result.getString("icon_key")); event.put("colorKey", result.getString("color_key"));
            }
        }
        ArrayNode tasks = event.putArray("tasks");
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement("SELECT * FROM tasks WHERE event_id=? ORDER BY rowid")) {
            statement.setString(1, eventId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    ObjectNode task = tasks.addObject();
                    task.put("id", result.getString("id")); task.put("kind", result.getString("kind")); task.put("title", result.getString("title")); putNullable(task, "dueDate", result.getString("due_date")); putNullable(task, "reminderAt", result.getString("reminder_at")); task.put("sourceText", result.getString("source_text")); task.put("decision", result.getString("decision")); task.put("completed", result.getInt("completed") == 1); putNullable(task, "completedAt", result.getString("completed_at"));
                }
            }
        }
        boolean hasOpenTask = false;
        boolean hasPastDueTask = false;
        LocalDate today = LocalDate.now();
        for (JsonNode task : tasks) {
            if (!task.path("completed").asBoolean()) {
                hasOpenTask = true;
                String dueDate = task.path("dueDate").asText("");
                if (!dueDate.isBlank() && LocalDate.parse(dueDate).isBefore(today)) hasPastDueTask = true;
            }
        }
        boolean schedulePast = false;
        if (event.path("recurrenceFrequency").asText("NONE").equals("NONE")) {
            String deadline = event.path("endsAt").asText(event.path("startsAt").asText(""));
            schedulePast = deadline.isBlank() ? LocalDate.parse(event.path("occurredOn").asText()).isBefore(today) : Instant.parse(deadline).isBefore(Instant.now());
        } else if (!event.path("recurrenceUntil").asText("").isBlank()) {
            schedulePast = LocalDate.parse(event.path("recurrenceUntil").asText()).isBefore(today);
        }
        event.put("overdue", hasPastDueTask || (hasOpenTask && schedulePast));
        return event;
    }

    private void updateEvent(HttpExchange exchange, String userId, String eventId) throws Exception {
        JsonNode body = readJson(exchange);
        ObjectNode current = loadEvent(userId, eventId);
        ObjectNode edited = current.deepCopy();
        copyIfPresent(body, edited, "eventSummary"); copyIfPresent(body, edited, "familyUpdate"); copyIfPresent(body, edited, "questionsForClinician"); copyIfPresent(body, edited, "occurredOn"); copySchedule(body, edited); copyAppearance(body, edited);
        validateSchedule(edited);
        validateAppearance(edited);
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement("UPDATE events SET event_summary=?,family_update=?,questions_json=?,occurred_on=?,timing_mode=?,starts_at=?,ends_at=?,recurrence_frequency=?,recurrence_interval=?,recurrence_until=?,icon_key=?,color_key=? WHERE id=? AND user_id=?")) {
            statement.setString(1, edited.path("eventSummary").asText()); statement.setString(2, edited.path("familyUpdate").asText()); statement.setString(3, JSON.writeValueAsString(edited.path("questionsForClinician"))); statement.setString(4, edited.path("occurredOn").asText()); statement.setString(5, edited.path("timingMode").asText()); statement.setString(6, nullableText(edited, "startsAt", null)); statement.setString(7, nullableText(edited, "endsAt", null)); statement.setString(8, edited.path("recurrenceFrequency").asText()); statement.setInt(9, edited.path("recurrenceInterval").asInt(1)); statement.setString(10, nullableText(edited, "recurrenceUntil", null)); statement.setString(11, edited.path("iconKey").asText()); statement.setString(12, edited.path("colorKey").asText()); statement.setString(13, eventId); statement.setString(14, userId);
            if (statement.executeUpdate() == 0) throw new ApiError(404, "NOT_FOUND", "Event not found.");
        }
        sendJson(exchange, 200, loadEvent(userId, eventId));
    }

    private void updateTask(HttpExchange exchange, String userId, String taskId) throws Exception {
        JsonNode body = readJson(exchange);
        Boolean completed = body.has("completed") ? body.path("completed").asBoolean() : null;
        String reminderAt = body.has("reminderAt") && !body.get("reminderAt").isNull() ? body.get("reminderAt").asText() : null;
        boolean touchesReminder = body.has("reminderAt");
        String title = optionalText(body, "title");
        String dueDate = body.has("dueDate") && !body.get("dueDate").isNull() ? body.get("dueDate").asText() : null;
        boolean touchesDueDate = body.has("dueDate");
        try (Connection connection = database.open()) {
            String eventId;
            try (PreparedStatement owner = connection.prepareStatement("SELECT t.event_id FROM tasks t JOIN events e ON e.id=t.event_id WHERE t.id=? AND e.user_id=?")) {
                owner.setString(1, taskId); owner.setString(2, userId);
                try (ResultSet result = owner.executeQuery()) { if (!result.next()) throw new ApiError(404, "NOT_FOUND", "Task not found."); eventId = result.getString(1); }
            }
            try (PreparedStatement update = connection.prepareStatement("UPDATE tasks SET completed=COALESCE(?,completed),completed_at=CASE WHEN ?=1 THEN ? WHEN ?=0 THEN NULL ELSE completed_at END,reminder_at=CASE WHEN ? THEN ? ELSE reminder_at END,title=COALESCE(?,title),due_date=CASE WHEN ? THEN ? ELSE due_date END WHERE id=?")) {
                if (completed == null) update.setNull(1, java.sql.Types.INTEGER); else update.setInt(1, completed ? 1 : 0);
                if (completed == null) update.setNull(2, java.sql.Types.INTEGER); else update.setInt(2, completed ? 1 : 0);
                update.setString(3, Instant.now().toString());
                if (completed == null) update.setNull(4, java.sql.Types.INTEGER); else update.setInt(4, completed ? 1 : 0);
                update.setBoolean(5, touchesReminder); update.setString(6, reminderAt); update.setString(7, title); update.setBoolean(8, touchesDueDate); update.setString(9, dueDate); update.setString(10, taskId); update.executeUpdate();
            }
            ObjectNode response = loadEvent(userId, eventId);
            JsonNode updated = response.withArray("tasks").findValue("id");
            for (JsonNode task : response.withArray("tasks")) if (task.path("id").asText().equals(taskId)) { updated = task; break; }
            sendJson(exchange, 200, updated);
        }
    }

    private void createExport(HttpExchange exchange, String userId) throws Exception {
        String id = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(10, ChronoUnit.MINUTES);
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement("INSERT INTO exports(id,user_id,expires_at) VALUES(?,?,?)")) {
            statement.setString(1, id); statement.setString(2, userId); statement.setString(3, expiresAt.toString()); statement.executeUpdate();
        }
        sendJson(exchange, 201, JSON.createObjectNode().put("downloadUrl", "/v1/exports/" + id).put("expiresAt", expiresAt.toString()));
    }

    private void downloadExport(HttpExchange exchange, String userId, String exportId) throws Exception {
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM exports WHERE id=? AND user_id=? AND expires_at>?")) {
            statement.setString(1, exportId); statement.setString(2, userId); statement.setString(3, Instant.now().toString());
            try (ResultSet result = statement.executeQuery()) { if (!result.next()) throw new ApiError(404, "NOT_FOUND", "Export link expired."); }
        }
        StringBuilder output = new StringBuilder("CareBinder confirmed plans\n\n");
        ArrayNode events = eventsFor(userId);
        for (JsonNode event : events) {
            output.append("Care event — ").append(event.path("occurredOn").asText());
            if (!event.path("startsAt").asText("").isBlank()) output.append(" — ").append(event.path("startsAt").asText());
            if (!event.path("endsAt").asText("").isBlank()) output.append(" to ").append(event.path("endsAt").asText());
            if (!event.path("recurrenceFrequency").asText("NONE").equals("NONE")) output.append(" — repeats ").append(event.path("recurrenceFrequency").asText().toLowerCase(Locale.ROOT)).append(event.path("recurrenceUntil").asText("").isBlank() ? "" : " until " + event.path("recurrenceUntil").asText());
            output.append("\n\nSummary\n").append(event.path("eventSummary").asText()).append("\n\nTasks\n");
            for (JsonNode task : event.withArray("tasks")) output.append("• ").append(task.path("title").asText()).append(task.path("completed").asBoolean() ? " (complete)" : "").append("\n");
            output.append("\nFamily update\n").append(event.path("familyUpdate").asText()).append("\n\n");
        }
        byte[] bytes = output.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"carebinder-export.txt\"");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private ArrayNode eventsFor(String userId) throws Exception {
        ArrayNode events = JSON.createArrayNode();
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement("SELECT id FROM events WHERE user_id=? ORDER BY occurred_on DESC,confirmed_at DESC")) {
            statement.setString(1, userId);
            try (ResultSet result = statement.executeQuery()) { while (result.next()) events.add(loadEvent(userId, result.getString(1))); }
        }
        return events;
    }

    private void deleteAccount(HttpExchange exchange, String userId) throws Exception {
        JsonNode body = readJson(exchange);
        if (!"DELETE".equals(body.path("confirmation").asText())) throw new ApiError(400, "VALIDATION_ERROR", "Type DELETE to confirm account deletion.");
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement("DELETE FROM users WHERE id=?")) {
            statement.setString(1, userId); statement.executeUpdate();
        }
        sendJson(exchange, 202, JSON.createObjectNode().put("deleted", true));
    }

    private ObjectNode requireDraft(String userId, String draftId) throws Exception {
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement("SELECT payload_json FROM drafts WHERE id=? AND user_id=?")) {
            statement.setString(1, draftId); statement.setString(2, userId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new ApiError(404, "DRAFT_EXPIRED", "Draft not found.");
                return (ObjectNode) JSON.readTree(result.getString(1));
            }
        }
    }

    private void requireRecipient(String userId, String recipientId) throws Exception {
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM recipients WHERE id=? AND user_id=?")) {
            statement.setString(1, recipientId); statement.setString(2, userId);
            try (ResultSet result = statement.executeQuery()) { if (!result.next()) throw new ApiError(403, "FORBIDDEN", "Care profile not found."); }
        }
    }

    private Asset requireAsset(String userId, String recipientId, String assetId) throws Exception {
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement("SELECT content_type,filename,bytes FROM assets WHERE id=? AND user_id=? AND recipient_id=?")) {
            statement.setString(1, assetId); statement.setString(2, userId); statement.setString(3, recipientId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new ApiError(403, "FORBIDDEN", "Source not found.");
                return new Asset(result.getString(1), result.getString(2), result.getBytes(3));
            }
        }
    }

    private String authenticatedUser(HttpExchange exchange) throws Exception {
        String token = bearer(exchange);
        if (token == null) return null;
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement("SELECT user_id FROM sessions WHERE token_hash=? AND expires_at>?")) {
            statement.setString(1, Security.tokenHash(token)); statement.setString(2, Instant.now().toString());
            try (ResultSet result = statement.executeQuery()) { return result.next() ? result.getString(1) : null; }
        }
    }

    private String bearer(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        return header != null && header.regionMatches(true, 0, "Bearer ", 0, 7) ? header.substring(7).strip() : null;
    }

    private JsonNode readJson(HttpExchange exchange) throws Exception {
        byte[] bytes = readBytes(exchange.getRequestBody(), MAX_JSON_BYTES);
        return bytes.length == 0 ? JSON.createObjectNode() : JSON.readTree(bytes);
    }

    private byte[] readBytes(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        for (int read; (read = input.read(buffer)) != -1;) {
            total += read;
            if (total > maxBytes) throw new ApiError(413, "PAYLOAD_TOO_LARGE", "That file is too large for this MVP.");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private String requiredText(JsonNode body, String field) {
        String value = optionalText(body, field);
        if (value == null || value.isBlank()) throw new ApiError(400, "VALIDATION_ERROR", field + " is required.");
        return value.strip();
    }

    private String optionalText(JsonNode body, String field) {
        JsonNode value = body.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String textOr(JsonNode body, String field, String fallback) {
        String value = optionalText(body, field);
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private String nullableText(JsonNode body, String field, String fallback) {
        return body.has(field) ? (body.get(field).isNull() ? null : body.get(field).asText()) : fallback;
    }

    private void copySchedule(JsonNode from, ObjectNode to) {
        copyIfPresent(from, to, "timingMode");
        copyIfPresent(from, to, "startsAt");
        copyIfPresent(from, to, "endsAt");
        copyIfPresent(from, to, "recurrenceFrequency");
        copyIfPresent(from, to, "recurrenceInterval");
        copyIfPresent(from, to, "recurrenceUntil");
    }

    private void copyAppearance(JsonNode from, ObjectNode to) {
        copyIfPresent(from, to, "iconKey");
        copyIfPresent(from, to, "colorKey");
    }

    private void validateAppearance(ObjectNode event) {
        String icon = event.path("iconKey").asText("note");
        String color = event.path("colorKey").asText("teal");
        if (!ICON_KEYS.contains(icon) || !COLOR_KEYS.contains(color)) {
            throw new ApiError(400, "VALIDATION_ERROR", "Choose a valid event icon and color.");
        }
        event.put("iconKey", icon);
        event.put("colorKey", color);
    }

    private void validateSchedule(ObjectNode schedule) {
        try {
            LocalDate date = LocalDate.parse(schedule.path("occurredOn").asText());
            String timingMode = schedule.path("timingMode").asText("ALL_DAY");
            String recurrence = schedule.path("recurrenceFrequency").asText("NONE");
            int interval = schedule.path("recurrenceInterval").asInt(1);
            if (!TIMING_MODES.contains(timingMode) || !RECURRENCE_FREQUENCIES.contains(recurrence) || interval < 1 || interval > 99) throw new IllegalArgumentException();
            if (timingMode.equals("ALL_DAY")) {
                schedule.putNull("startsAt"); schedule.putNull("endsAt");
            } else {
                String startsAt = requiredText(schedule, "startsAt");
                Instant start = Instant.parse(startsAt);
                if (timingMode.equals("AT_TIME")) schedule.putNull("endsAt");
                else {
                    Instant end = Instant.parse(requiredText(schedule, "endsAt"));
                    if (!end.isAfter(start)) throw new IllegalArgumentException();
                }
            }
            if (recurrence.equals("NONE")) schedule.putNull("recurrenceUntil");
            else {
                String until = optionalText(schedule, "recurrenceUntil");
                if (until != null && LocalDate.parse(until).isBefore(date)) throw new IllegalArgumentException();
            }
            schedule.put("timingMode", timingMode); schedule.put("recurrenceFrequency", recurrence); schedule.put("recurrenceInterval", interval);
        } catch (Exception error) {
            if (error instanceof ApiError apiError) throw apiError;
            throw new ApiError(400, "VALIDATION_ERROR", "Choose a valid event date, time range, and recurrence.");
        }
    }

    private void copyIfPresent(JsonNode from, ObjectNode to, String field) { if (from.has(field)) to.set(field, from.get(field)); }
    private Set<String> draftItemIds(JsonNode draft) {
        Set<String> ids = new HashSet<>();
        draft.path("tasks").forEach(item -> ids.add(item.path("id").asText()));
        draft.path("medicationItems").forEach(item -> ids.add(item.path("id").asText()));
        return ids;
    }
    private void putNullable(ObjectNode object, String field, String value) { if (value == null) object.putNull(field); else object.put(field, value); }
    private String tail(String path) { return path.substring(path.lastIndexOf('/') + 1); }
    private String safeFilename(String filename) { return filename.replaceAll("[^a-zA-Z0-9._-]", "_"); }

    private String queryParameter(HttpExchange exchange, String name) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null) return null;
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts[0].equals(name) && parts.length == 2) return java.net.URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
        }
        return null;
    }

    private void addCommonHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Authorization, Content-Type");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
    }

    private void sendJson(HttpExchange exchange, int status, JsonNode body) throws IOException {
        byte[] bytes = JSON.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private void sendError(HttpExchange exchange, int status, String code, String message, boolean retryable) throws IOException {
        ObjectNode body = JSON.createObjectNode().put("code", code).put("message", message).put("retryable", retryable);
        sendJson(exchange, status, body);
    }

    private void serveWeb(HttpExchange exchange, String path) throws IOException {
        String resource = switch (path) {
            case "/", "/index.html" -> "/web/index.html";
            case "/app.js" -> "/web/app.js";
            case "/styles.css" -> "/web/styles.css";
            case "/sw.js" -> "/web/sw.js";
            default -> null;
        };
        if (resource == null) { sendError(exchange, 404, "NOT_FOUND", "Page not found.", false); return; }
        try (InputStream input = ApiServer.class.getResourceAsStream(resource)) {
            if (input == null) { sendError(exchange, 404, "NOT_FOUND", "Page not found.", false); return; }
            byte[] bytes = input.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", resource.endsWith(".js") ? "text/javascript; charset=utf-8" : resource.endsWith(".css") ? "text/css; charset=utf-8" : "text/html; charset=utf-8");
            if (resource.endsWith("sw.js")) exchange.getResponseHeaders().set("Service-Worker-Allowed", "/");
            exchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'self'; style-src 'self' https://accounts.google.com/gsi/style; script-src 'self' https://accounts.google.com/gsi/client; img-src 'self' data: https://lh3.googleusercontent.com; connect-src 'self' https://accounts.google.com/gsi/; frame-src https://accounts.google.com/gsi/");
            exchange.getResponseHeaders().set("Cross-Origin-Opener-Policy", "same-origin-allow-popups");
            if (exchange.getRequestMethod().equals("HEAD")) { exchange.sendResponseHeaders(200, -1); return; }
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }

    private record Asset(String contentType, String filename, byte[] bytes) {}

    private static final class ApiError extends RuntimeException {
        final int status;
        final String code;
        final boolean retryable;
        ApiError(int status, String code, String message) { this(status, code, message, false); }
        ApiError(int status, String code, String message, boolean retryable) { super(message); this.status = status; this.code = code; this.retryable = retryable; }
    }
}
