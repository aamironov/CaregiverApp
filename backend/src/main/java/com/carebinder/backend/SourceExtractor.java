package com.carebinder.backend;

interface SourceExtractor {
    record Source(String text, String kind, String model) {}

    boolean enabled();

    Source extract(String contentType, String filename, byte[] bytes) throws Exception;

    static SourceExtractor disabled() {
        return new SourceExtractor() {
            @Override public boolean enabled() { return false; }
            @Override public Source extract(String contentType, String filename, byte[] bytes) {
                throw new IllegalStateException("Source extraction is disabled.");
            }
        };
    }
}
