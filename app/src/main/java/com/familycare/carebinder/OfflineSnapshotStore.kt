package com.familycare.carebinder

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.familycare.carebinder.data.remote.Recipient
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class OfflineSnapshot(
    val recipient: Recipient,
    val events: List<UiEvent>,
    val drafts: List<UiEvent>,
    val language: String,
    val savedAt: String,
)

internal class OfflineSnapshotStore(context: Context) {
    private val preferences = context.getSharedPreferences("carebinder_offline", Context.MODE_PRIVATE)

    fun save(recipient: Recipient, events: List<UiEvent>, drafts: List<UiEvent>, language: String) {
        val root = JSONObject()
            .put("recipient", JSONObject().put("id", recipient.id).put("displayName", recipient.displayName).put("relationship", recipient.relationship))
            .put("events", JSONArray(events.map(::eventJson)))
            .put("drafts", JSONArray(drafts.map(::eventJson)))
            .put("language", language)
            .put("savedAt", java.time.Instant.now().toString())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal(root.toString().toByteArray(Charsets.UTF_8))
        preferences.edit()
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("payload", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    fun load(): OfflineSnapshot? = runCatching {
        val iv = Base64.decode(preferences.getString("iv", null) ?: return null, Base64.NO_WRAP)
        val payload = Base64.decode(preferences.getString("payload", null) ?: return null, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
        val root = JSONObject(String(cipher.doFinal(payload), Charsets.UTF_8))
        val recipientJson = root.getJSONObject("recipient")
        OfflineSnapshot(
            recipient = Recipient(recipientJson.getString("id"), recipientJson.getString("displayName"), recipientJson.getString("relationship")),
            events = parseEvents(root.getJSONArray("events"), confirmed = true),
            drafts = parseEvents(root.getJSONArray("drafts"), confirmed = false),
            language = root.optString("language", "en"),
            savedAt = root.optString("savedAt"),
        )
    }.getOrNull()

    fun clear() { preferences.edit().clear().apply() }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
            generateKey()
        }
    }

    private fun eventJson(event: UiEvent): JSONObject = JSONObject()
        .put("id", event.id).put("recipientId", event.recipientId).put("date", event.date).put("sourceLabel", event.sourceLabel)
        .put("assetId", event.assetId ?: JSONObject.NULL).put("summary", event.summary).put("familyUpdate", event.familyUpdate)
        .put("translatedSummary", event.translatedSummary ?: JSONObject.NULL).put("translatedFamilyUpdate", event.translatedFamilyUpdate ?: JSONObject.NULL)
        .put("tasks", JSONArray(event.tasks.map(::taskJson))).put("medicationItems", JSONArray(event.medicationItems.map(::taskJson)))
        .put("questions", JSONArray(event.clinicianQuestions)).put("confirmed", event.confirmed).put("timingMode", event.timingMode)
        .put("startsAt", event.startsAt ?: JSONObject.NULL).put("endsAt", event.endsAt ?: JSONObject.NULL)
        .put("recurrenceFrequency", event.recurrenceFrequency).put("recurrenceUntil", event.recurrenceUntil ?: JSONObject.NULL)
        .put("iconKey", event.iconKey).put("colorKey", event.colorKey).put("overdue", event.overdue)

    private fun taskJson(task: UiTask): JSONObject = JSONObject()
        .put("id", task.id).put("kind", task.kind).put("title", task.title).put("sourceText", task.sourceText)
        .put("dueDate", task.dueDate ?: JSONObject.NULL).put("reminderAt", task.reminderAt ?: JSONObject.NULL)
        .put("reviewed", task.reviewed).put("removed", task.removed).put("isComplete", task.isComplete)
        .put("translatedTitle", task.translatedTitle ?: JSONObject.NULL).put("translatedSourceText", task.translatedSourceText ?: JSONObject.NULL)

    private fun parseEvents(array: JSONArray, confirmed: Boolean): List<UiEvent> = (0 until array.length()).map { index ->
        val item = array.getJSONObject(index); val tasks = item.getJSONArray("tasks"); val medication = item.getJSONArray("medicationItems"); val questions = item.getJSONArray("questions")
        UiEvent(
            id = item.getString("id"), recipientId = item.getString("recipientId"), date = item.getString("date"), sourceLabel = item.getString("sourceLabel"), assetId = item.optNullable("assetId"),
            summary = item.getString("summary"), familyUpdate = item.getString("familyUpdate"), translatedSummary = item.optNullable("translatedSummary"), translatedFamilyUpdate = item.optNullable("translatedFamilyUpdate"),
            tasks = (0 until tasks.length()).map { parseTask(tasks.getJSONObject(it)) }, medicationItems = (0 until medication.length()).map { parseTask(medication.getJSONObject(it)) },
            clinicianQuestions = (0 until questions.length()).map { questions.getString(it) }, confirmed = confirmed, timingMode = item.optString("timingMode", "ALL_DAY"),
            startsAt = item.optNullable("startsAt"), endsAt = item.optNullable("endsAt"), recurrenceFrequency = item.optString("recurrenceFrequency", "NONE"), recurrenceUntil = item.optNullable("recurrenceUntil"),
            iconKey = item.optString("iconKey", "note"), colorKey = item.optString("colorKey", "teal"), overdue = item.optBoolean("overdue"),
        )
    }

    private fun parseTask(item: JSONObject) = UiTask(
        id = item.getString("id"), kind = item.getString("kind"), title = item.getString("title"), sourceText = item.getString("sourceText"),
        dueDate = item.optNullable("dueDate"), reminderAt = item.optNullable("reminderAt"), reviewed = item.optBoolean("reviewed"), removed = item.optBoolean("removed"), isComplete = item.optBoolean("isComplete"),
        translatedTitle = item.optNullable("translatedTitle"), translatedSourceText = item.optNullable("translatedSourceText"),
    )

    private fun JSONObject.optNullable(name: String): String? = if (isNull(name)) null else optString(name).ifBlank { null }

    companion object { private const val KEY_ALIAS = "carebinder_offline_snapshot_v1" }
}
