package com.familycare.carebinder.core.privacy

/**
 * Guards product behavior, not just UI wording. Any generated health-related content is a
 * reviewable draft until the caregiver confirms it. Raw documents and extracted contents must
 * never be written to analytics, crash reports, or application logs.
 */
object PrivacyBoundary {
    const val HEALTH_DISCLAIMER =
        "CareBinder organizes information and does not provide medical advice. Verify details " +
            "against the original documents and contact a qualified clinician with questions."

    fun isShareAllowed(isConfirmedByCaregiver: Boolean): Boolean = isConfirmedByCaregiver
}
