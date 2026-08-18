package com.familycare.carebinder.core.model

import java.time.Instant
import java.time.LocalDate

data class CareRecipient(
    val id: String,
    val displayName: String,
    val relationship: String,
)

data class CareEvent(
    val id: String,
    val recipientId: String,
    val occurredAt: Instant,
    val sourceType: SourceType,
    val status: ReviewStatus,
    val summary: String?,
)

enum class SourceType { DOCUMENT, VOICE_NOTE, TYPED_NOTE }
enum class ReviewStatus { DRAFT, CONFIRMED }

data class CareTask(
    val id: String,
    val eventId: String,
    val title: String,
    val dueDate: LocalDate?,
    val sourceText: String,
    val needsConfirmation: Boolean = true,
    val isComplete: Boolean = false,
)

data class ExtractionDraft(
    val eventSummary: String,
    val tasks: List<CareTask>,
    val medicationItems: List<String>,
    val questionsForClinician: List<String>,
    val familyUpdate: String,
)
