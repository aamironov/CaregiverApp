package com.carebinder.backend;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BytezSourceExtractorTest {
    @Test
    void sendsAudioAsDataUriAndReadsTranscription() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/models/v2/test-speech", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = "{\"error\":null,\"output\":\"Call the clinic tomorrow.\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            BytezSourceExtractor extractor = new BytezSourceExtractor(
                "test-key", "", "http://127.0.0.1:" + server.getAddress().getPort() + "/models/v2",
                "test-document", "test-speech", HttpClient.newHttpClient()
            );
            SourceExtractor.Source source = extractor.extract("audio/wav", "voice.wav", new byte[]{1, 2, 3});
            assertEquals("Call the clinic tomorrow.", source.text());
            assertEquals("BYTEZ_SPEECH", source.kind());
            assertEquals("test-key", authorization.get());
            assertTrue(requestBody.get().contains("data:audio/wav;base64,AQID"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void translatesWithTargetLanguageInstruction() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/models/v2/test-translation", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"error\":null,\"output\":\"Llame a la clínica.\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            BytezTranslator translator = new BytezTranslator("test-key", "", "http://127.0.0.1:" + server.getAddress().getPort() + "/models/v2", "test-translation", HttpClient.newHttpClient());
            assertEquals("Llame a la clínica.", translator.translate("Call the clinic.", "es"));
            assertTrue(requestBody.get().contains("Spanish"));
            assertTrue(requestBody.get().contains("Call the clinic."));
        } finally {
            server.stop(0);
        }
    }
}
