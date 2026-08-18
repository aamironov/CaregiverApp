package com.carebinder.backend;

interface TextTranslator {
    boolean enabled();
    String translate(String text, String targetLanguage) throws Exception;

    static TextTranslator disabled() {
        return new TextTranslator() {
            @Override public boolean enabled() { return false; }
            @Override public String translate(String text, String targetLanguage) { return text; }
        };
    }
}
