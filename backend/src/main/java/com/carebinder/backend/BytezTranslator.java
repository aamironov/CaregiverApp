package com.carebinder.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

final class BytezTranslator implements TextTranslator {
    private static final Map<String, String> LANGUAGE_NAMES = Map.of("en", "English", "ru", "Russian", "es", "Spanish");
    private final String apiKey;
    private final String providerKey;
    private final String baseUrl;
    private final String model;
    private final HttpClient client;

    static TextTranslator fromEnvironment() {
        String apiKey = System.getenv().getOrDefault("BYTEZ_API_KEY", "").trim();
        if (apiKey.isEmpty()) return TextTranslator.disabled();
        return new BytezTranslator(
            apiKey,
            System.getenv().getOrDefault("BYTEZ_PROVIDER_KEY", "").trim(),
            System.getenv().getOrDefault("BYTEZ_BASE_URL", "https://api.bytez.com/models/v2").trim(),
            System.getenv().getOrDefault("BYTEZ_TRANSLATION_MODEL", "Qwen/Qwen3-4B").trim(),
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()
        );
    }

    BytezTranslator(String apiKey, String providerKey, String baseUrl, String model, HttpClient client) {
        this.apiKey = apiKey;
        this.providerKey = providerKey;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.model = model;
        this.client = client;
    }

    @Override public boolean enabled() { return !apiKey.isBlank(); }

    @Override
    public String translate(String text, String targetLanguage) throws Exception {
        if (text == null || text.isBlank() || targetLanguage.equals("en") && text.chars().allMatch(value -> value < 128)) return text;
        String language = LANGUAGE_NAMES.get(targetLanguage);
        if (language == null) throw new IllegalArgumentException("Unsupported language.");
        ObjectNode payload = ApiServer.JSON.createObjectNode();
        ArrayNode messages = payload.putArray("messages");
        messages.addObject().put("role", "system").put("content", "Translate the user text into " + language + ". Preserve names, dates, times, quantities, formatting, and meaning. Return only the translation. Do not add medical advice or commentary.");
        messages.addObject().put("role", "user").put("content", text);
        payload.putObject("params").put("temperature", 0);

        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + "/" + model))
            .timeout(Duration.ofSeconds(120))
            .header("Authorization", apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(ApiServer.JSON.writeValueAsString(payload)));
        if (!providerKey.isBlank()) request.header("provider-key", providerKey);
        HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        JsonNode body = ApiServer.JSON.readTree(response.body());
        if (response.statusCode() < 200 || response.statusCode() >= 300 || body.hasNonNull("error")) {
            throw new TranslationException(response.statusCode() == 429 ? "Translation is rate limited. Try again shortly." : "Translation is temporarily unavailable.", response.statusCode() == 429 || response.statusCode() >= 500);
        }
        String output = body.path("output").asText("").strip();
        if (output.isBlank()) throw new TranslationException("Translation returned no text.", false);
        return output;
    }

    static final class TranslationException extends Exception {
        final boolean retryable;
        TranslationException(String message, boolean retryable) { super(message); this.retryable = retryable; }
    }
}
