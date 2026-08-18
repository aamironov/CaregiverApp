package com.carebinder.backend;

import java.nio.file.Path;

public final class App {
    private App() {}

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        Path databasePath = Path.of(System.getenv().getOrDefault("CAREBINDER_DB", "data/carebinder.db"));
        Database database = new Database(databasePath);
        database.initialize();
        GoogleAuthService googleAuth = GoogleAuthService.fromEnvironment(System.getenv().getOrDefault("GOOGLE_CLIENT_IDS", ""));
        SourceExtractor sourceExtractor = BytezSourceExtractor.fromEnvironment();
        TextTranslator translator = BytezTranslator.fromEnvironment();
        ApiServer server = new ApiServer(database, port, googleAuth, sourceExtractor, translator);
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        System.out.printf("CareBinder listening on http://localhost:%d%n", server.port());
        System.out.printf("Google sign-in: %s%n", googleAuth.enabled() ? "enabled" : "disabled");
        System.out.printf("Bytez source extraction: %s%n", sourceExtractor.enabled() ? "enabled" : "disabled");
        System.out.printf("Bytez translation: %s%n", translator.enabled() ? "enabled" : "disabled");
    }
}
