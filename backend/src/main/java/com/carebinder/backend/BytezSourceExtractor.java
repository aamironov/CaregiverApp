package com.carebinder.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

final class BytezSourceExtractor implements SourceExtractor {
    private static final int MAX_PDF_PAGES = 12;
    private static final String DOCUMENT_PROMPT = "Transcribe every visible word from this care document in reading order. Preserve dates, times, names, instructions, and list items. Return only the transcription; do not interpret or add advice.";

    private final String apiKey;
    private final String providerKey;
    private final String baseUrl;
    private final String documentModel;
    private final String speechModel;
    private final HttpClient client;

    static SourceExtractor fromEnvironment() {
        String apiKey = System.getenv().getOrDefault("BYTEZ_API_KEY", "").trim();
        if (apiKey.isEmpty()) return SourceExtractor.disabled();
        return new BytezSourceExtractor(
            apiKey,
            System.getenv().getOrDefault("BYTEZ_PROVIDER_KEY", "").trim(),
            System.getenv().getOrDefault("BYTEZ_BASE_URL", "https://api.bytez.com/models/v2").trim(),
            System.getenv().getOrDefault("BYTEZ_DOCUMENT_MODEL", "Qwen/Qwen2.5-VL-7B-Instruct").trim(),
            System.getenv().getOrDefault("BYTEZ_SPEECH_MODEL", "openai/whisper-large-v3-turbo").trim(),
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()
        );
    }

    BytezSourceExtractor(String apiKey, String providerKey, String baseUrl, String documentModel, String speechModel, HttpClient client) {
        this.apiKey = apiKey;
        this.providerKey = providerKey;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.documentModel = documentModel;
        this.speechModel = speechModel;
        this.client = client;
    }

    @Override public boolean enabled() { return !apiKey.isBlank(); }

    @Override
    public Source extract(String contentType, String filename, byte[] bytes) throws Exception {
        try {
            if (bytes == null || bytes.length == 0) throw new ExtractionException("The uploaded source is empty.", false);
            String normalized = contentType == null ? "application/octet-stream" : contentType.toLowerCase();
            if (normalized.startsWith("audio/")) {
                ObjectNode payload = ApiServer.JSON.createObjectNode().put("base64", dataUri(normalized, bytes));
                return new Source(run(speechModel, payload), "BYTEZ_SPEECH", speechModel);
            }
            if (normalized.startsWith("image/")) {
                return new Source(extractImage(normalized, bytes), "BYTEZ_DOCUMENT", documentModel);
            }
            if (normalized.equals("application/pdf")) {
                return new Source(extractPdf(bytes), "BYTEZ_DOCUMENT", documentModel);
            }
            throw new ExtractionException("Bytez extraction supports image, PDF, and audio files.", false);
        } catch (ExtractionException error) {
            throw error;
        } catch (Exception error) {
            throw new ExtractionException("The document or recording could not be prepared for Bytez extraction.", false, error);
        }
    }

    private String extractPdf(byte[] bytes) throws Exception {
        List<String> pages = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.getNumberOfPages() > MAX_PDF_PAGES) {
                throw new ExtractionException("PDFs are limited to " + MAX_PDF_PAGES + " pages for automatic extraction.", false);
            }
            PDFRenderer renderer = new PDFRenderer(document);
            for (int page = 0; page < document.getNumberOfPages(); page++) {
                ByteArrayOutputStream image = new ByteArrayOutputStream();
                ImageIO.write(renderer.renderImageWithDPI(page, 160, ImageType.RGB), "png", image);
                pages.add("Page " + (page + 1) + ":\n" + extractImage("image/png", image.toByteArray()));
            }
        }
        return String.join("\n\n", pages).strip();
    }

    private String extractImage(String contentType, byte[] bytes) throws Exception {
        ObjectNode payload = ApiServer.JSON.createObjectNode();
        ArrayNode messages = payload.putArray("messages");
        ObjectNode message = messages.addObject().put("role", "user");
        ArrayNode content = message.putArray("content");
        content.addObject().put("type", "text").put("text", DOCUMENT_PROMPT);
        content.addObject().put("type", "image").put("base64", dataUri(contentType, bytes));
        return run(documentModel, payload);
    }

    private String run(String model, ObjectNode payload) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + "/" + model))
            .timeout(Duration.ofSeconds(120))
            .header("Authorization", apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(ApiServer.JSON.writeValueAsString(payload)));
        if (!providerKey.isBlank()) request.header("provider-key", providerKey);
        HttpResponse<String> response;
        try {
            response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception error) {
            throw new ExtractionException("Bytez could not be reached. Try again.", true, error);
        }
        JsonNode body;
        try {
            body = ApiServer.JSON.readTree(response.body());
        } catch (Exception error) {
            throw new ExtractionException("Bytez returned an unreadable response.", true, error);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300 || !body.path("error").isNull() && !body.path("error").isMissingNode()) {
            String detail = body.path("error").asText("").strip();
            String message = response.statusCode() == 401 ? "Bytez authentication failed. Check the backend API key."
                : response.statusCode() == 429 ? "Bytez is rate limited. Try again shortly."
                : detail.isBlank() ? "Bytez could not process this source." : "Bytez could not process this source: " + detail;
            throw new ExtractionException(message, response.statusCode() == 429 || response.statusCode() >= 500);
        }
        String output = outputText(body.path("output")).strip();
        if (output.isBlank()) throw new ExtractionException("Bytez did not find usable text in this source.", false);
        return output;
    }

    private String outputText(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return "";
        if (node.isTextual()) return node.asText();
        if (node.isArray()) {
            List<String> values = new ArrayList<>();
            node.forEach(item -> { String value = outputText(item); if (!value.isBlank()) values.add(value); });
            return String.join("\n", values);
        }
        for (String key : List.of("text", "markdown", "content", "transcript", "pages")) {
            if (node.has(key)) {
                String value = outputText(node.get(key));
                if (!value.isBlank()) return value;
            }
        }
        return "";
    }

    private String dataUri(String contentType, byte[] bytes) {
        return "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    static final class ExtractionException extends Exception {
        final boolean retryable;
        ExtractionException(String message, boolean retryable) { super(message); this.retryable = retryable; }
        ExtractionException(String message, boolean retryable, Throwable cause) { super(message, cause); this.retryable = retryable; }
    }
}
