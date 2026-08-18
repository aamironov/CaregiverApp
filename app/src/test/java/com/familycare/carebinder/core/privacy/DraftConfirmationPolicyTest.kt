package com.familycare.carebinder.core.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftConfirmationPolicyTest {
    @Test
    fun `confirmation is blocked when a generated item remains unreviewed`() {
        assertFalse(DraftConfirmationPolicy.canConfirm(reviewedGeneratedItemCount = 1, generatedItemCount = 2))
    }

    @Test
    fun `confirmation is allowed only when every generated item was reviewed`() {
        assertTrue(DraftConfirmationPolicy.canConfirm(reviewedGeneratedItemCount = 2, generatedItemCount = 2))
    }

    @Test
    fun `removed items are excluded from the confirmed plan`() {
        val saved = DraftConfirmationPolicy.confirmedItemsOnly(
            listOf(
                ConfirmedItem("keep", ReviewDecision.ACCEPTED),
                ConfirmedItem("edit", ReviewDecision.EDITED),
                ConfirmedItem("remove", ReviewDecision.REMOVED),
            ),
        )

        assertEquals(listOf("keep", "edit"), saved.map { it.id })
    }
}
