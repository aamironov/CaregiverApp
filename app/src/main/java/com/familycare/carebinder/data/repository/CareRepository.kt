package com.familycare.carebinder.data.repository

import com.familycare.carebinder.core.model.CareEvent
import com.familycare.carebinder.core.model.CareRecipient
import com.familycare.carebinder.core.model.ExtractionDraft

/**
 * UI code only knows this interface. The remote implementation will use an authenticated backend;
 * it must not call Gemini or hold an API key from the Android client.
 */
interface CareRepository {
    suspend fun recipients(): List<CareRecipient>
    suspend fun saveReviewedEvent(event: CareEvent, draft: ExtractionDraft)
}
