package com.carebinder.backend;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class DraftGenerator {
    private DraftGenerator() {}

    static ObjectNode generate(String draftId, String recipientId, String sourceType, String assetId, String source) {
        String cleaned = source.strip();
        ObjectNode draft = ApiServer.JSON.createObjectNode();
        draft.put("draftId", draftId);
        draft.put("recipientId", recipientId);
        draft.put("sourceType", sourceType);
        if (assetId != null) draft.put("assetId", assetId);
        draft.put("eventSummary", cleaned);
        draft.put("familyUpdate", "Care update: " + cleaned);
        draft.put("occurredOn", LocalDate.now().toString());
        draft.put("timingMode", "ALL_DAY");
        draft.putNull("startsAt");
        draft.putNull("endsAt");
        draft.put("recurrenceFrequency", "NONE");
        draft.put("recurrenceInterval", 1);
        draft.putNull("recurrenceUntil");
        draft.put("iconKey", switch (sourceType) {
            case "DOCUMENT" -> "document";
            case "VOICE_NOTE" -> "voice";
            default -> "note";
        });
        draft.put("colorKey", "teal");

        List<String> sentences = Arrays.stream(cleaned.split("[.!?\\n]+"))
            .map(String::strip)
            .filter(value -> value.length() >= 4)
            .limit(6)
            .toList();
        if (sentences.isEmpty()) sentences = List.of("Review the source and identify the next step");

        ArrayNode tasks = draft.putArray("tasks");
        for (String sentence : sentences) {
            ObjectNode task = tasks.addObject();
            task.put("id", UUID.randomUUID().toString());
            task.put("title", sentence.substring(0, 1).toUpperCase(Locale.ROOT) + sentence.substring(1));
            task.put("kind", "TASK");
            task.putNull("dueDate");
            task.put("sourceText", sentence);
            task.put("needsReview", true);
        }
        draft.putArray("medicationItems");
        draft.putArray("questionsForClinician").add("What should I confirm at the follow-up?");
        draft.putArray("warnings").add("This is a draft. Check it against the original source before confirming.");
        return draft;
    }
}
