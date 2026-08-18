package com.familycare.carebinder.data.remote

import com.familycare.carebinder.UiEvent
import com.familycare.carebinder.UiTask
import com.familycare.carebinder.ScheduleInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Session(val accessToken: String, val email: String)
data class Recipient(val id: String, val displayName: String, val relationship: String)
data class UploadSource(val filename: String, val contentType: String, val bytes: ByteArray)
data class UserSettings(val language: String, val translationEnabled: Boolean)

class ApiException(val status: Int, message: String) : Exception(message)

internal class BackendApi(private val baseUrl: String) {
    suspend fun register(email: String, password: String): Session = auth("register", email, password)
    suspend fun login(email: String, password: String): Session = auth("login", email, password)

    suspend fun googleLogin(idToken: String): Session {
        val json = requestObject("POST", "/v1/auth/google", body = JSONObject().put("credential", idToken))
        return Session(json.getString("accessToken"), json.getJSONObject("user").getString("email"))
    }

    private suspend fun auth(action: String, email: String, password: String): Session {
        val json = requestObject("POST", "/v1/auth/$action", body = JSONObject().put("email", email).put("password", password))
        return Session(json.getString("accessToken"), json.getJSONObject("user").getString("email"))
    }

    suspend fun logout(token: String) { requestText("POST", "/v1/auth/logout", token) }

    suspend fun settings(token: String): UserSettings {
        val json = requestObject("GET", "/v1/settings", token)
        return UserSettings(json.optString("language", "en"), json.optBoolean("translationEnabled", false))
    }

    suspend fun updateLanguage(token: String, language: String): UserSettings {
        val json = requestObject("PATCH", "/v1/settings", token, JSONObject().put("language", language))
        return UserSettings(json.optString("language", "en"), json.optBoolean("translationEnabled", false))
    }

    suspend fun translatedEvents(token: String, language: String, enabled: Boolean, values: List<UiEvent>): List<UiEvent> {
        if (language == "en" || !enabled) return values
        val originals = values.flatMap { event -> listOf(event.summary, event.familyUpdate) + event.tasks.flatMap { listOf(it.title, it.sourceText) } }.distinct().take(40)
        if (originals.isEmpty()) return values
        val body = JSONObject().put("texts", JSONArray(originals))
        val response = requestObject("POST", "/v1/translations", token, body).optJSONArray("translations") ?: return values
        val translated = originals.mapIndexed { index, text -> text to response.optString(index, text) }.toMap()
        return values.map { event -> event.copy(
            translatedSummary = translated[event.summary], translatedFamilyUpdate = translated[event.familyUpdate],
            tasks = event.tasks.map { task -> task.copy(translatedTitle = translated[task.title], translatedSourceText = translated[task.sourceText]) }
        ) }
    }

    suspend fun recipient(token: String): Recipient? = try {
        parseRecipient(requestObject("GET", "/v1/care-recipients/me", token))
    } catch (error: ApiException) {
        if (error.status == 404) null else throw error
    }

    suspend fun createRecipient(token: String, name: String, relationship: String): Recipient =
        parseRecipient(requestObject("POST", "/v1/care-recipients", token, JSONObject().put("displayName", name).put("relationship", relationship)))

    suspend fun updateRecipient(token: String, name: String, relationship: String): Recipient =
        parseRecipient(requestObject("PATCH", "/v1/care-recipients/me", token, JSONObject().put("displayName", name).put("relationship", relationship)))

    suspend fun events(token: String, recipientId: String): List<UiEvent> =
        parseArray(requestArray("GET", "/v1/events?recipientId=$recipientId", token)) { parseEvent(it, confirmed = true) }

    suspend fun drafts(token: String): List<UiEvent> =
        parseArray(requestArray("GET", "/v1/drafts", token)) { parseEvent(it, confirmed = false) }

    suspend fun createDraft(
        token: String,
        recipientId: String,
        sourceType: String,
        note: String,
        schedule: ScheduleInput,
        source: UploadSource?,
    ): UiEvent {
        val assetId = source?.let { upload(token, recipientId, it) }
        val body = JSONObject()
            .put("recipientId", recipientId)
            .put("sourceType", sourceType)
            .put("typedNote", note)
            .putSchedule(schedule)
        if (assetId != null) body.put("assetId", assetId)
        val created = requestObject("POST", "/v1/events/drafts", token, body)
        return saveDraft(token, parseEvent(created, confirmed = false))
    }

    private suspend fun upload(token: String, recipientId: String, source: UploadSource): String {
        val reservation = requestObject(
            "POST", "/v1/events/uploads", token,
            JSONObject().put("recipientId", recipientId).put("contentType", source.contentType).put("filename", source.filename),
        )
        requestText("PUT", reservation.getString("uploadUrl"), token, raw = source.bytes, contentType = source.contentType)
        return reservation.getString("assetId")
    }

    suspend fun saveDraft(token: String, draft: UiEvent): UiEvent =
        parseEvent(requestObject("PUT", "/v1/drafts/${draft.id}", token, draftJson(draft)), confirmed = false)

    suspend fun deleteDraft(token: String, draftId: String) { requestText("DELETE", "/v1/drafts/$draftId", token) }

    suspend fun confirm(token: String, draft: UiEvent): UiEvent {
        val items = JSONArray()
        draft.tasks.forEach { task ->
            items.put(
                JSONObject()
                    .put("draftItemId", task.id)
                    .put("decision", if (task.removed) "removed" else if (task.title == task.sourceText) "accepted" else "edited")
                    .put("title", task.title)
                    .put("dueDate", task.dueDate ?: JSONObject.NULL)
                    .put("reminderAt", task.reminderAt ?: JSONObject.NULL)
            )
        }
        val body = JSONObject()
            .put("draftId", draft.id)
            .put("recipientId", draft.recipientId)
            .put("eventSummary", draft.summary)
            .put("familyUpdate", draft.familyUpdate)
            .put("occurredOn", draft.date)
            .put("timingMode", draft.timingMode)
            .put("startsAt", draft.startsAt ?: JSONObject.NULL)
            .put("endsAt", draft.endsAt ?: JSONObject.NULL)
            .put("recurrenceFrequency", draft.recurrenceFrequency)
            .put("recurrenceInterval", 1)
            .put("recurrenceUntil", draft.recurrenceUntil ?: JSONObject.NULL)
            .put("iconKey", draft.iconKey)
            .put("colorKey", draft.colorKey)
            .put("questionsForClinician", JSONArray(draft.clinicianQuestions))
            .put("items", items)
        return parseEvent(requestObject("POST", "/v1/events/confirm", token, body), confirmed = true)
    }

    suspend fun updateEvent(token: String, event: UiEvent): UiEvent =
        parseEvent(requestObject("PATCH", "/v1/events/${event.id}", token, draftJson(event)), confirmed = true)

    suspend fun updateTask(token: String, taskId: String, completed: Boolean? = null, title: String? = null, dueDate: String? = null, touchDueDate: Boolean = false, reminderAt: String? = null, touchReminder: Boolean = false): UiTask {
        val body = JSONObject()
        if (completed != null) body.put("completed", completed)
        if (title != null) body.put("title", title)
        if (touchDueDate) body.put("dueDate", dueDate ?: JSONObject.NULL)
        if (touchReminder) body.put("reminderAt", reminderAt ?: JSONObject.NULL)
        return parseTask(requestObject("PATCH", "/v1/tasks/$taskId", token, body))
    }

    suspend fun export(token: String, recipientId: String): ByteArray {
        val created = requestObject("POST", "/v1/exports", token, JSONObject().put("recipientId", recipientId).put("format", "text"))
        return requestBytes("GET", created.getString("downloadUrl"), token)
    }

    suspend fun deleteAccount(token: String) {
        requestObject("DELETE", "/v1/account", token, JSONObject().put("confirmation", "DELETE"))
    }

    private fun parseRecipient(json: JSONObject) = Recipient(json.getString("id"), json.getString("displayName"), json.getString("relationship"))

    private fun parseEvent(json: JSONObject, confirmed: Boolean): UiEvent {
        val taskArray = json.optJSONArray("tasks") ?: JSONArray()
        val questions = json.optJSONArray("questionsForClinician") ?: JSONArray()
        return UiEvent(
            id = json.optString(if (confirmed) "id" else "draftId"),
            recipientId = json.getString("recipientId"),
            date = json.optString("occurredOn", java.time.LocalDate.now().toString()),
            sourceLabel = json.optString("sourceType", "TYPED_NOTE"),
            assetId = json.optString("assetId").ifBlank { null },
            summary = json.optString("eventSummary"),
            tasks = (0 until taskArray.length()).map { parseTask(taskArray.getJSONObject(it)) },
            medicationItems = (json.optJSONArray("medicationItems") ?: JSONArray()).let { array -> (0 until array.length()).map { parseTask(array.getJSONObject(it)) } },
            clinicianQuestions = (0 until questions.length()).map { questions.optString(it) },
            confirmed = confirmed,
            familyUpdate = json.optString("familyUpdate"),
            timingMode = json.optString("timingMode", "ALL_DAY"),
            startsAt = json.optString("startsAt").ifBlank { null },
            endsAt = json.optString("endsAt").ifBlank { null },
            recurrenceFrequency = json.optString("recurrenceFrequency", "NONE"),
            recurrenceUntil = json.optString("recurrenceUntil").ifBlank { null },
            iconKey = json.optString("iconKey", "note"),
            colorKey = json.optString("colorKey", "teal"),
            overdue = json.optBoolean("overdue", false),
        )
    }

    private fun parseTask(json: JSONObject) = UiTask(
        id = json.getString("id"),
        kind = json.optString("kind", "TASK"),
        title = json.optString("title"),
        sourceText = json.optString("sourceText"),
        dueDate = json.optString("dueDate").ifBlank { null },
        reminderAt = json.optString("reminderAt").ifBlank { null },
        reviewed = json.optBoolean("reviewed", false),
        removed = json.optBoolean("removed", false),
        isComplete = json.optBoolean("completed", false),
    )

    private fun draftJson(draft: UiEvent): JSONObject = JSONObject()
        .put("eventSummary", draft.summary)
        .put("familyUpdate", draft.familyUpdate)
        .put("occurredOn", draft.date)
        .put("timingMode", draft.timingMode)
        .put("startsAt", draft.startsAt ?: JSONObject.NULL)
        .put("endsAt", draft.endsAt ?: JSONObject.NULL)
        .put("recurrenceFrequency", draft.recurrenceFrequency)
        .put("recurrenceInterval", 1)
        .put("recurrenceUntil", draft.recurrenceUntil ?: JSONObject.NULL)
        .put("iconKey", draft.iconKey)
        .put("colorKey", draft.colorKey)
        .put("questionsForClinician", JSONArray(draft.clinicianQuestions))
        .put("tasks", JSONArray(draft.tasks.map { taskJson(it) }))
        .put("medicationItems", JSONArray(draft.medicationItems.map { taskJson(it) }))

    private fun taskJson(task: UiTask) = JSONObject()
        .put("id", task.id).put("kind", task.kind).put("title", task.title).put("sourceText", task.sourceText)
        .put("dueDate", task.dueDate ?: JSONObject.NULL).put("reminderAt", task.reminderAt ?: JSONObject.NULL)
        .put("reviewed", task.reviewed).put("removed", task.removed).put("needsReview", true)

    private fun JSONObject.putSchedule(schedule: ScheduleInput): JSONObject {
        val zone = java.time.ZoneId.systemDefault()
        val startsAt = if (schedule.timingMode == "ALL_DAY") null else java.time.LocalDateTime.parse("${schedule.date}T${schedule.startTime}").atZone(zone).toInstant().toString()
        val endsAt = if (schedule.timingMode == "TIME_RANGE") java.time.LocalDateTime.parse("${schedule.endDate}T${schedule.endTime}").atZone(zone).toInstant().toString() else null
        return put("occurredOn", schedule.date).put("timingMode", schedule.timingMode).put("startsAt", startsAt ?: JSONObject.NULL).put("endsAt", endsAt ?: JSONObject.NULL)
            .put("recurrenceFrequency", schedule.recurrenceFrequency).put("recurrenceInterval", 1).put("recurrenceUntil", schedule.recurrenceUntil.ifBlank { null } ?: JSONObject.NULL)
    }

    private inline fun <T> parseArray(array: JSONArray, parser: (JSONObject) -> T): List<T> =
        (0 until array.length()).map { parser(array.getJSONObject(it)) }

    private suspend fun requestObject(method: String, path: String, token: String? = null, body: JSONObject? = null): JSONObject =
        JSONObject(requestText(method, path, token, body?.toString()))

    private suspend fun requestArray(method: String, path: String, token: String? = null): JSONArray =
        JSONArray(requestText(method, path, token))

    private suspend fun requestText(
        method: String,
        path: String,
        token: String? = null,
        body: String? = null,
        raw: ByteArray? = null,
        contentType: String = "application/json",
    ): String = withContext(Dispatchers.IO) {
        val connection = open(method, path, token, contentType)
        if (body != null || raw != null) connection.outputStream.use { it.write(raw ?: body!!.toByteArray()) }
        val status = connection.responseCode
        val response = (if (status >= 400) connection.errorStream else connection.inputStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        if (status >= 400) throw ApiException(status, runCatching { JSONObject(response).optString("message") }.getOrNull().orEmpty().ifBlank { "Request failed." })
        response
    }

    private suspend fun requestBytes(method: String, path: String, token: String): ByteArray = withContext(Dispatchers.IO) {
        val connection = open(method, path, token, "application/json")
        val status = connection.responseCode
        if (status >= 400) {
            val message = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()
            throw ApiException(status, runCatching { JSONObject(message).optString("message") }.getOrNull().orEmpty().ifBlank { "Request failed." })
        }
        connection.inputStream.use { it.readBytes() }.also { connection.disconnect() }
    }

    private fun open(method: String, path: String, token: String?, contentType: String): HttpURLConnection =
        (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12_000
            readTimeout = 300_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", contentType)
            if (token != null) setRequestProperty("Authorization", "Bearer $token")
            doOutput = method in setOf("POST", "PUT", "PATCH", "DELETE")
        }
}
