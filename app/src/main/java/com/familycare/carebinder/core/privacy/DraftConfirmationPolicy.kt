package com.familycare.carebinder.core.privacy

/**
 * Server-side confirmation must enforce the same rule described here. A generated item can only
 * become durable after the caregiver has explicitly reviewed it or removed it.
 */
object DraftConfirmationPolicy {
    fun canConfirm(reviewedGeneratedItemCount: Int, generatedItemCount: Int): Boolean {
        require(reviewedGeneratedItemCount >= 0) { "Reviewed item count cannot be negative." }
        require(generatedItemCount >= 0) { "Generated item count cannot be negative." }
        return reviewedGeneratedItemCount == generatedItemCount
    }

    fun confirmedItemsOnly(items: List<ConfirmedItem>): List<ConfirmedItem> =
        items.filter { it.decision != ReviewDecision.REMOVED }
}

enum class ReviewDecision { ACCEPTED, EDITED, REMOVED }

data class ConfirmedItem(
    val id: String,
    val decision: ReviewDecision,
)
