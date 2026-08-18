package com.carebinder.backend;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

final class GoogleAuthService {
    record Identity(String subject, String email, boolean authoritativeEmail) {}

    @FunctionalInterface
    interface TokenVerifier {
        Identity verify(String credential) throws Exception;
    }

    private final List<String> clientIds;
    private final TokenVerifier verifier;

    GoogleAuthService(List<String> clientIds, TokenVerifier verifier) {
        this.clientIds = List.copyOf(clientIds);
        this.verifier = verifier;
    }

    static GoogleAuthService disabled() {
        return new GoogleAuthService(List.of(), credential -> null);
    }

    static GoogleAuthService fromEnvironment(String configuredClientIds) {
        List<String> clientIds = Arrays.stream(configuredClientIds.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .distinct()
            .toList();
        if (clientIds.isEmpty()) return disabled();

        GoogleIdTokenVerifier googleVerifier = new GoogleIdTokenVerifier.Builder(
            new NetHttpTransport(), GsonFactory.getDefaultInstance())
            .setAudience(clientIds)
            .build();
        return new GoogleAuthService(clientIds, credential -> {
            GoogleIdToken token = googleVerifier.verify(credential);
            if (token == null) return null;
            GoogleIdToken.Payload payload = token.getPayload();
            Object verified = payload.get("email_verified");
            if (!(verified instanceof Boolean value && value)) return null;
            String subject = payload.getSubject();
            String email = payload.getEmail();
            if (subject == null || subject.isBlank() || email == null || email.isBlank()) return null;
            String normalizedEmail = email.toLowerCase(Locale.ROOT);
            boolean authoritative = normalizedEmail.endsWith("@gmail.com") ||
                (payload.getHostedDomain() != null && !payload.getHostedDomain().isBlank());
            return new Identity(subject, normalizedEmail, authoritative);
        });
    }

    boolean enabled() { return !clientIds.isEmpty(); }
    String webClientId() { return enabled() ? clientIds.getFirst() : ""; }

    Identity verify(String credential) throws Exception {
        if (!enabled()) return null;
        return verifier.verify(credential);
    }
}
