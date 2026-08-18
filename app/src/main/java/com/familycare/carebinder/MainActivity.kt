package com.familycare.carebinder

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.familycare.carebinder.core.privacy.DraftConfirmationPolicy
import com.familycare.carebinder.core.privacy.PrivacyBoundary
import com.familycare.carebinder.data.remote.BackendApi
import com.familycare.carebinder.data.remote.ApiException
import com.familycare.carebinder.data.remote.Recipient
import com.familycare.carebinder.data.remote.UploadSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CareBinderTheme { CareBinderApp(BackendApi(BuildConfig.CAREBINDER_API_BASE_URL)) } }
    }
}

internal data class UiTask(
    val id: String = UUID.randomUUID().toString(),
    val kind: String = "TASK",
    val title: String,
    val sourceText: String,
    val dueDate: String? = null,
    val reminderAt: String? = null,
    val reviewed: Boolean = false,
    val removed: Boolean = false,
    val isComplete: Boolean = false,
    val translatedTitle: String? = null,
    val translatedSourceText: String? = null,
)

internal data class UiEvent(
    val id: String,
    val recipientId: String,
    val date: String = LocalDate.now().toString(),
    val sourceLabel: String,
    val assetId: String? = null,
    val summary: String,
    val tasks: List<UiTask>,
    val medicationItems: List<UiTask> = emptyList(),
    val clinicianQuestions: List<String> = emptyList(),
    val confirmed: Boolean,
    val familyUpdate: String,
    val timingMode: String = "ALL_DAY",
    val startsAt: String? = null,
    val endsAt: String? = null,
    val recurrenceFrequency: String = "NONE",
    val recurrenceUntil: String? = null,
    val iconKey: String = "note",
    val colorKey: String = "teal",
    val overdue: Boolean = false,
    val translatedSummary: String? = null,
    val translatedFamilyUpdate: String? = null,
)

private val UiTask.displayTitle get() = translatedTitle ?: title
private val UiTask.displaySourceText get() = translatedSourceText ?: sourceText
private val UiEvent.displaySummary get() = translatedSummary ?: summary
private val UiEvent.displayFamilyUpdate get() = translatedFamilyUpdate ?: familyUpdate

private val eventIcons = listOf("note" to "✎", "document" to "▤", "voice" to "◉", "medical" to "+", "calendar" to "◷", "meal" to "♨", "sleep" to "☾", "activity" to "☆")
private val eventColors = linkedMapOf(
    "slate" to Color(0xFF64748B), "gray" to Color(0xFF78716C), "red" to Color(0xFFDC2626), "orange" to Color(0xFFEA580C),
    "amber" to Color(0xFFD97706), "yellow" to Color(0xFFCA8A04), "lime" to Color(0xFF65A30D), "green" to Color(0xFF16A34A),
    "emerald" to Color(0xFF059669), "teal" to Color(0xFF0D9488), "cyan" to Color(0xFF0891B2), "sky" to Color(0xFF0284C7),
    "blue" to Color(0xFF2563EB), "indigo" to Color(0xFF4F46E5), "violet" to Color(0xFF7C3AED), "pink" to Color(0xFFDB2777),
)

internal data class ScheduleInput(
    val date: String = LocalDate.now().toString(),
    val timingMode: String = "ALL_DAY",
    val startTime: String = "09:00",
    val endDate: String = date,
    val endTime: String = "10:00",
    val recurrenceFrequency: String = "NONE",
    val recurrenceUntil: String = "",
)

private enum class Destination(val label: String) {
    TODAY("Today"), TIMELINE("Timeline"), ADD("Add"), UPDATES("Updates"), PROFILE("Profile")
}

private val LocalLanguage = staticCompositionLocalOf { "en" }
private val mobileTranslations = mapOf(
    "ru" to mapOf("Today" to "Сегодня", "Timeline" to "Хронология", "Add" to "Добавить", "Updates" to "Обновления", "Profile" to "Профиль", "Add a care event" to "Добавить событие ухода", "Scheduled events" to "Запланированные события", "Open tasks" to "Открытые задачи", "Past due" to "Просрочено", "Ready to review" to "Готово к проверке", "Review draft" to "Проверить черновик", "Edit care event" to "Изменить событие", "Event appearance" to "Внешний вид события", "Language" to "Язык", "English" to "Английский", "Russian" to "Русский", "Spanish" to "Испанский", "Save language" to "Сохранить язык"),
    "es" to mapOf("Today" to "Hoy", "Timeline" to "Cronología", "Add" to "Añadir", "Updates" to "Actualizaciones", "Profile" to "Perfil", "Add a care event" to "Añadir evento de cuidado", "Scheduled events" to "Eventos programados", "Open tasks" to "Tareas abiertas", "Past due" to "Atrasado", "Ready to review" to "Listo para revisar", "Review draft" to "Revisar borrador", "Edit care event" to "Editar evento", "Event appearance" to "Apariencia del evento", "Language" to "Idioma", "English" to "Inglés", "Russian" to "Ruso", "Spanish" to "Español", "Save language" to "Guardar idioma"),
)

@Composable private fun ui(text: String): String = mobileTranslations[LocalLanguage.current]?.get(text) ?: text

@Composable
private fun CareBinderTheme(content: @Composable () -> Unit) {
    val colors: ColorScheme = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = Color(0xFF86CCB9), secondaryContainer = Color(0xFF254137), surface = Color(0xFF171E1A), background = Color(0xFF101512))
    } else {
        lightColorScheme(primary = Color(0xFF245F50), secondaryContainer = Color(0xFFDCEBE5), surface = Color(0xFFFFFDF8), background = Color(0xFFF4F0E8))
    }
    MaterialTheme(colorScheme = colors, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CareBinderApp(api: BackendApi) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("carebinder_session", Context.MODE_PRIVATE) }
    var token by remember { mutableStateOf(preferences.getString("token", null)) }
    var language by remember { mutableStateOf(preferences.getString("language", "en") ?: "en") }
    var translationEnabled by remember { mutableStateOf(false) }
    var recipient by remember { mutableStateOf<Recipient?>(null) }
    val events = remember { mutableStateListOf<UiEvent>() }
    val drafts = remember { mutableStateListOf<UiEvent>() }
    var loading by remember { mutableStateOf(token != null) }
    var destination by remember { mutableStateOf(Destination.TODAY) }
    var reviewEvent by remember { mutableStateOf<UiEvent?>(null) }
    var editingEvent by remember { mutableStateOf<UiEvent?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val credentialManager = remember { CredentialManager.create(context) }
    val offlineStore = remember { OfflineSnapshotStore(context) }
    var offline by remember { mutableStateOf(false) }

    LaunchedEffect(language) { Locale.setDefault(Locale.forLanguageTag(language)) }

    suspend fun refresh() {
        val activeToken = token ?: return
        recipient = api.recipient(activeToken)
        val settings = api.settings(activeToken); language = settings.language; translationEnabled = settings.translationEnabled; preferences.edit().putString("language", language).apply()
        events.clear(); drafts.clear()
        recipient?.let {
            val originals = api.events(activeToken, it.id); val originalDrafts = api.drafts(activeToken)
            events.addAll(runCatching { api.translatedEvents(activeToken, language, settings.translationEnabled, originals) }.getOrDefault(originals))
            drafts.addAll(runCatching { api.translatedEvents(activeToken, language, settings.translationEnabled, originalDrafts) }.getOrDefault(originalDrafts))
            offlineStore.save(it, events, drafts, language)
        }
        offline = false
    }

    fun restoreOffline(): Boolean {
        val snapshot = offlineStore.load() ?: return false
        recipient = snapshot.recipient; events.clear(); events.addAll(snapshot.events); drafts.clear(); drafts.addAll(snapshot.drafts)
        language = snapshot.language; offline = true
        return true
    }

    fun report(error: Throwable) { scope.launch { snackbar.showSnackbar(error.message ?: "Something went wrong.") } }

    LaunchedEffect(token) {
        if (token != null) {
            loading = true
            runCatching { refresh() }.onFailure { error ->
                if (error is ApiException && error.status == 401) { preferences.edit().remove("token").apply(); offlineStore.clear(); token = null; recipient = null }
                else if (restoreOffline()) snackbar.showSnackbar("Offline — showing the last encrypted event snapshot.")
                else { report(error); preferences.edit().remove("token").apply(); token = null }
            }
            loading = false
        }
    }

    CompositionLocalProvider(LocalLanguage provides language) { Surface(Modifier.fillMaxSize()) {
        when {
            token == null -> AuthScreen(api, credentialManager, onAuthenticated = { session ->
                preferences.edit().putString("token", session.accessToken).apply()
                loading = true; token = session.accessToken
            })
            loading -> LoadingScreen()
            recipient == null -> ProfileSetupScreen(onSave = { name, relationship ->
                scope.launch {
                    runCatching { api.createRecipient(token!!, name, relationship) }
                        .onSuccess { recipient = it; destination = Destination.TODAY }
                        .onFailure(::report)
                }
            })
            else -> Scaffold(
                snackbarHost = { SnackbarHost(snackbar) },
                topBar = { if (offline) Surface(color = MaterialTheme.colorScheme.tertiaryContainer) { Text("Offline — cached events", Modifier.fillMaxWidth().padding(10.dp), fontWeight = FontWeight.SemiBold) } },
                bottomBar = {
                    NavigationBar {
                        Destination.entries.forEach { item ->
                            NavigationBarItem(
                                selected = destination == item,
                                onClick = { reviewEvent = null; editingEvent = null; destination = item },
                                icon = { Text(if (item == Destination.ADD) "+" else ui(item.label).take(1)) },
                                label = { Text(ui(item.label)) },
                            )
                        }
                    }
                },
            ) { padding ->
                when {
                    editingEvent != null -> EventEditScreen(
                        event = editingEvent!!,
                        onBack = { editingEvent = null },
                        onSave = { edited -> scope.launch { runCatching { api.updateEvent(token!!, edited) }.onSuccess { refresh(); editingEvent = null; destination = Destination.TIMELINE; snackbar.showSnackbar("Event updated.") }.onFailure(::report) } },
                        modifier = Modifier.padding(padding),
                    )
                    reviewEvent != null -> ReviewScreen(
                        event = reviewEvent!!,
                        onBack = { reviewEvent = null },
                        onSaveDraft = { edited ->
                            scope.launch {
                                runCatching { api.saveDraft(token!!, edited) }.onSuccess { saved ->
                                    drafts.removeAll { it.id == saved.id }; drafts.add(0, saved); reviewEvent = null; destination = Destination.TIMELINE
                                    offlineStore.save(recipient!!, events, drafts, language)
                                    snackbar.showSnackbar("Draft saved.")
                                }.onFailure(::report)
                            }
                        },
                        onConfirm = { edited ->
                            scope.launch {
                                runCatching { api.confirm(token!!, edited) }.onSuccess { confirmed ->
                                    val displayed = runCatching { api.translatedEvents(token!!, language, translationEnabled, listOf(confirmed)).first() }.getOrDefault(confirmed)
                                    drafts.removeAll { it.id == edited.id }; events.add(0, displayed); reviewEvent = null; destination = Destination.TODAY
                                    offlineStore.save(recipient!!, events, drafts, language)
                                    displayed.tasks.forEach { scheduleReminder(context, it) }
                                    if (displayed.tasks.any { it.reminderAt != null } && Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    snackbar.showSnackbar("Confirmed plan saved.")
                                }.onFailure(::report)
                            }
                        },
                        modifier = Modifier.padding(padding),
                    )
                    destination == Destination.TODAY -> TodayScreen(
                        recipientName = recipient!!.displayName,
                        events = events,
                        drafts = drafts,
                        onAdd = { destination = Destination.ADD },
                        onReview = { reviewEvent = it },
                        onEventEdit = { editingEvent = it },
                        onTaskChecked = { task ->
                            scope.launch {
                                runCatching { api.updateTask(token!!, task.id, completed = !task.isComplete) }
                                    .onSuccess { refresh(); snackbar.showSnackbar(if (it.isComplete) "Task completed." else "Task reopened.") }
                                    .onFailure(::report)
                            }
                        },
                        onReminder = { task, value ->
                            scope.launch {
                                runCatching { api.updateTask(token!!, task.id, reminderAt = value, touchReminder = true) }
                                    .onSuccess { updated -> refresh(); scheduleReminder(context, updated); snackbar.showSnackbar("Reminder saved.") }
                                    .onFailure(::report)
                            }
                        },
                        onTaskEdit = { task, title, dueDate, reminderAt ->
                            scope.launch {
                                runCatching { api.updateTask(token!!, task.id, title = title, dueDate = dueDate, touchDueDate = true, reminderAt = reminderAt, touchReminder = true) }
                                    .onSuccess { refresh(); snackbar.showSnackbar("Task updated.") }.onFailure(::report)
                            }
                        },
                        modifier = Modifier.padding(padding),
                    )
                    destination == Destination.TIMELINE -> TimelineScreen(events, drafts, { reviewEvent = it }, { editingEvent = it }, Modifier.padding(padding))
                    destination == Destination.ADD -> AddEventScreen(
                        onCreateDraft = { note, sourceType, schedule, source ->
                            val draft = api.createDraft(token!!, recipient!!.id, sourceType, note, schedule, source)
                            val displayed = runCatching { api.translatedEvents(token!!, language, translationEnabled, listOf(draft)).first() }.getOrDefault(draft)
                            drafts.add(0, displayed); reviewEvent = displayed
                            offlineStore.save(recipient!!, events, drafts, language)
                        },
                        modifier = Modifier.padding(padding),
                    )
                    destination == Destination.UPDATES -> UpdatesScreen(events, Modifier.padding(padding))
                    else -> ProfileScreen(
                        recipient = recipient!!,
                        language = language,
                        onLanguage = { selected -> scope.launch { runCatching { api.updateLanguage(token!!, selected) }.onSuccess { language = it.language; preferences.edit().putString("language", language).apply(); refresh(); snackbar.showSnackbar("Language updated.") }.onFailure(::report) } },
                        onSave = { name, relationship ->
                            scope.launch { runCatching { api.updateRecipient(token!!, name, relationship) }.onSuccess { recipient = it; offlineStore.save(it, events, drafts, language); snackbar.showSnackbar("Profile saved.") }.onFailure(::report) }
                        },
                        onExport = {
                            scope.launch {
                                runCatching { api.export(token!!, recipient!!.id) }.onSuccess { bytes ->
                                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, bytes.toString(Charsets.UTF_8)) }, "Export confirmed CareBinder plans"))
                                }.onFailure(::report)
                            }
                        },
                        onSignOut = {
                            scope.launch {
                                runCatching { api.logout(token!!) }
                                runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
                                preferences.edit().clear().apply(); offlineStore.clear(); token = null; recipient = null; events.clear(); drafts.clear()
                            }
                        },
                        onDelete = {
                            scope.launch {
                                runCatching { api.deleteAccount(token!!) }.onSuccess {
                                    runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
                                    preferences.edit().clear().apply(); offlineStore.clear(); token = null; recipient = null; events.clear(); drafts.clear()
                                }.onFailure(::report)
                            }
                        },
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }
    } }
}

@Composable
private fun AuthScreen(api: BackendApi, credentialManager: CredentialManager, onAuthenticated: (com.familycare.carebinder.data.remote.Session) -> Unit) {
    var register by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    ScreenColumn {
        Spacer(Modifier.height(36.dp))
        Text("CareBinder", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text("Keep the next steps from getting lost.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("You review every detail before anything is confirmed or shared.")
        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()) {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        submitting = true; error = null
                        runCatching {
                            val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()
                            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                            val result = credentialManager.getCredential(context = context, request = request)
                            val credential = result.credential
                            if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                error("Google returned an unsupported credential.")
                            }
                            api.googleLogin(GoogleIdTokenCredential.createFrom(credential.data).idToken)
                        }.onSuccess(onAuthenticated).onFailure { error = it.message ?: "Google sign-in was not completed." }
                        submitting = false
                    }
                },
                enabled = !submitting,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Continue with Google") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text("or use email", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { register = false; error = null }, enabled = register) { Text("Sign in") }
            OutlinedButton(onClick = { register = true; error = null }, enabled = !register) { Text("Create account") }
        }
        OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password (8+ characters)") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                scope.launch {
                    submitting = true; error = null
                    runCatching { if (register) api.register(email.trim(), password) else api.login(email.trim(), password) }
                        .onSuccess(onAuthenticated).onFailure { error = it.message }
                    submitting = false
                }
            },
            enabled = !submitting && email.contains("@") && password.length >= 8,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (submitting) "Please wait…" else if (register) "Create account" else "Sign in") }
        Text(PrivacyBoundary.HEALTH_DISCLAIMER, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun LoadingScreen() = Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() } }

@Composable
private fun ProfileSetupScreen(onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }; var relationship by remember { mutableStateOf("") }
    ScreenColumn {
        Spacer(Modifier.height(36.dp)); Text("Set up care profile", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Start with the person you’re helping. This MVP supports one care recipient.")
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Preferred name") })
        OutlinedTextField(relationship, { relationship = it }, Modifier.fillMaxWidth(), label = { Text("Your relationship") })
        Button({ onSave(name.trim(), relationship.trim()) }, Modifier.fillMaxWidth(), enabled = name.isNotBlank() && relationship.isNotBlank()) { Text("Create care profile") }
    }
}

@Composable
private fun TodayScreen(
    recipientName: String,
    events: List<UiEvent>,
    drafts: List<UiEvent>,
    onAdd: () -> Unit,
    onReview: (UiEvent) -> Unit,
    onEventEdit: (UiEvent) -> Unit,
    onTaskChecked: (UiTask) -> Unit,
    onReminder: (UiTask, String?) -> Unit,
    onTaskEdit: (UiTask, String, String?, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val overdueEvents = events.filter { it.overdue }
    val upcomingEvents = events.filterNot { it.overdue }.sortedBy { it.startsAt ?: "${it.date}T00:00:00Z" }
    val tasks = events.flatMap { it.tasks }.filterNot { it.isComplete }.sortedByDescending(::isTaskOverdue)
    var reminderTask by remember { mutableStateOf<UiTask?>(null) }
    var editingTask by remember { mutableStateOf<UiTask?>(null) }
    ScreenColumn(modifier) {
        Text(ui("Today"), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold); Text("For $recipientName")
        Button(onAdd, Modifier.fillMaxWidth()) { Text(ui("Add a care event")) }
        if (overdueEvents.isNotEmpty()) {
            Text(ui("Past due"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
            Text("Unfinished events stay at the top until their tasks are complete.", style = MaterialTheme.typography.bodySmall)
            overdueEvents.forEach { event -> EventSummaryCard(event, overdue = true) { onEventEdit(event) } }
        }
        Text(ui("Scheduled events"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        if (upcomingEvents.isEmpty()) EmptyCard("No scheduled events", "Add an all-day, timed, or recurring care event.") else upcomingEvents.forEach { event -> EventSummaryCard(event, overdue = false) { onEventEdit(event) } }
        if (tasks.isEmpty()) EmptyCard("No open tasks yet", "Add paperwork or a note after the next visit, then review the plan together.")
        else { Text(ui("Open tasks"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold); tasks.forEach { TaskCard(it, { onTaskChecked(it) }, { reminderTask = it }, { editingTask = it }) } }
        if (drafts.isNotEmpty()) {
            Text(ui("Ready to review"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            drafts.forEach { draft -> DraftSummaryCard(draft) { onReview(draft) } }
        }
    }
    reminderTask?.let { task -> ReminderDialog(task, { reminderTask = null }, { value -> onReminder(task, value); reminderTask = null }) }
    editingTask?.let { task -> TaskEditDialog(task, { editingTask = null }, { title, dueDate, reminderAt -> onTaskEdit(task, title, dueDate, reminderAt); editingTask = null }) }
}

@Composable
private fun ReminderDialog(task: UiTask, onDismiss: () -> Unit, onSave: (String?) -> Unit) {
    var value by remember(task.id) { mutableStateOf(task.reminderAt?.take(16) ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Task reminder") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(task.title); OutlinedTextField(value, { value = it }, label = { Text("YYYY-MM-DDTHH:mm") }); Text("Use local date and 24-hour time, or leave blank to remove.", style = MaterialTheme.typography.bodySmall) } },
        confirmButton = { TextButton({ onSave(value.trim().ifBlank { null }) }) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TimelineScreen(events: List<UiEvent>, drafts: List<UiEvent>, onReview: (UiEvent) -> Unit, onEdit: (UiEvent) -> Unit, modifier: Modifier = Modifier) {
    ScreenColumn(modifier) {
        Text(ui("Timeline"), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        drafts.forEach { draft -> DraftSummaryCard(draft) { onReview(draft) } }
        if (events.isEmpty()) EmptyCard("No confirmed events", "Your reviewed plans will appear here.")
        events.forEach { event -> EventSummaryCard(event, event.overdue) { onEdit(event) } }
    }
}

@Composable
private fun AddEventScreen(
    onCreateDraft: suspend (String, String, ScheduleInput, UploadSource?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sourceType by remember { mutableStateOf("TYPED_NOTE") }
    var note by remember { mutableStateOf("") }
    var schedule by remember { mutableStateOf(ScheduleInput()) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var cameraBytes by remember { mutableStateOf<ByteArray?>(null) }
    var selectedName by remember { mutableStateOf<String?>(null) }
    var contentType by remember { mutableStateOf("application/octet-stream") }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { selectedUri = it; cameraBytes = null; selectedName = it.lastPathSegment?.substringAfterLast('/') ?: "Selected source"; contentType = context.contentResolver.getType(it) ?: "application/octet-stream"; sourceType = if (contentType.startsWith("audio")) "VOICE_NOTE" else "DOCUMENT" } }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap -> bitmap?.let { val output = ByteArrayOutputStream(); it.compress(Bitmap.CompressFormat.JPEG, 88, output); cameraBytes = output.toByteArray(); selectedUri = null; selectedName = "care-document.jpg"; contentType = "image/jpeg"; sourceType = "DOCUMENT" } }
    val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoiceRecording(context.cacheDir, { active, file -> recorder = active; recordingFile = file; selectedName = "Recording in progress"; sourceType = "VOICE_NOTE" }, { error = it }) else error = "Microphone access is needed to record a voice note."
    }
    DisposableEffect(Unit) { onDispose { recorder?.release() } }
    ScreenColumn(modifier) {
        Text(ui("Add a care event"), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Choose a document or recording. Bytez extracts its wording into suggested tasks for you to review before saving.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton({ camera.launch(null) }) { Text("Take photo") }; OutlinedButton({ picker.launch(arrayOf("image/*", "application/pdf", "audio/*", "text/plain")) }) { Text("Choose file") } }
        if (recorder == null) OutlinedButton({ microphone.launch(Manifest.permission.RECORD_AUDIO) }, Modifier.fillMaxWidth()) { Text("Record voice note") }
        else Button({ val ok = runCatching { recorder?.stop() }.isSuccess; recorder?.release(); recorder = null; if (ok) { selectedName = recordingFile?.name; contentType = "audio/mp4" } else error = "The recording could not be saved." }, Modifier.fillMaxWidth()) { Text("Stop recording") }
        selectedName?.let { Text("Selected: $it", style = MaterialTheme.typography.bodySmall) }
        ScheduleEditor(schedule, { schedule = it })
        OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("Optional caregiver note") }, minLines = 4)
        Text("The Bytez API key stays on the backend. Extracted text remains a draft until you review and confirm every task.", style = MaterialTheme.typography.bodySmall)
        Text(PrivacyBoundary.HEALTH_DISCLAIMER, style = MaterialTheme.typography.bodySmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                scope.launch {
                    submitting = true; error = null
                    runCatching {
                        val bytes = withContext(Dispatchers.IO) { cameraBytes ?: selectedUri?.let { context.contentResolver.openInputStream(it)?.use { stream -> stream.readBytes() } } ?: recordingFile?.takeIf { it.exists() }?.readBytes() }
                        val source = bytes?.let { UploadSource(selectedName ?: "source", contentType, it) }
                        onCreateDraft(note.trim(), sourceType, schedule, source)
                    }.onFailure { error = it.message }
                    submitting = false
                }
            }, enabled = (note.isNotBlank() || selectedUri != null || cameraBytes != null || recordingFile?.exists() == true) && !submitting, modifier = Modifier.fillMaxWidth(),
        ) { Text(if (submitting) "Extracting and creating draft…" else "Create draft to review") }
    }
}

@Composable
private fun ReviewScreen(event: UiEvent, onBack: () -> Unit, onSaveDraft: (UiEvent) -> Unit, onConfirm: (UiEvent) -> Unit, modifier: Modifier = Modifier) {
    var summary by remember(event.id) { mutableStateOf(event.summary) }; var update by remember(event.id) { mutableStateOf(event.familyUpdate) }
    var schedule by remember(event.id) { mutableStateOf(event.toScheduleInput()) }; var tasks by remember(event.id) { mutableStateOf(event.tasks) }; var questions by remember(event.id) { mutableStateOf(event.clinicianQuestions) }; var confirmDialog by remember { mutableStateOf(false) }
    var iconKey by remember(event.id) { mutableStateOf(event.iconKey) }; var colorKey by remember(event.id) { mutableStateOf(event.colorKey) }
    val visibleTasks = tasks.filterNot { it.removed }
    val allReviewed = DraftConfirmationPolicy.canConfirm(visibleTasks.count { it.reviewed }, visibleTasks.size)
    val edited = { event.withSchedule(schedule).copy(summary = summary, familyUpdate = update, tasks = tasks, clinicianQuestions = questions, iconKey = iconKey, colorKey = colorKey) }
    ScreenColumn(modifier) {
        TextButton(onBack) { Text("Back") }; Text(ui("Review draft"), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) { Text("Check this against the original before confirming.", Modifier.padding(16.dp)) }
        OutlinedTextField(summary, { summary = it }, Modifier.fillMaxWidth(), label = { Text("Draft summary") }); ScheduleEditor(schedule, { schedule = it })
        AppearanceEditor(iconKey, colorKey) { icon, color -> iconKey = icon; colorKey = color }
        Text("Tasks and deadlines", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        visibleTasks.forEach { task ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedTextField(task.title, { revised -> tasks = tasks.map { if (it.id == task.id) it.copy(title = revised) else it } }, Modifier.fillMaxWidth(), label = { Text("Task") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(task.dueDate.orEmpty(), { value -> tasks = tasks.map { if (it.id == task.id) it.copy(dueDate = value.ifBlank { null }) else it } }, Modifier.weight(1f), label = { Text("Due date") }); OutlinedTextField(task.reminderAt.orEmpty(), { value -> tasks = tasks.map { if (it.id == task.id) it.copy(reminderAt = value.ifBlank { null }) else it } }, Modifier.weight(1f), label = { Text("Reminder") }) }
                Text("Source: ${task.sourceText}", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton({ tasks = tasks.map { if (it.id == task.id) it.copy(reviewed = !it.reviewed) else it } }) { Text(if (task.reviewed) "Reviewed" else "Mark reviewed") }; TextButton({ tasks = tasks.map { if (it.id == task.id) it.copy(removed = true) else it } }) { Text("Remove") } }
            } }
        }
        Text("Questions for the clinician", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        questions.forEachIndexed { index, question -> OutlinedTextField(question, { revised -> questions = questions.mapIndexed { itemIndex, value -> if (itemIndex == index) revised else value } }, Modifier.fillMaxWidth(), label = { Text("Question") }) }
        TextButton({ questions = questions + "" }) { Text("Add clinician question") }
        OutlinedTextField(update, { update = it }, Modifier.fillMaxWidth(), label = { Text("Family update") }, minLines = 3)
        OutlinedButton({ onSaveDraft(edited()) }, Modifier.fillMaxWidth()) { Text("Save as draft") }
        Button({ confirmDialog = true }, Modifier.fillMaxWidth(), enabled = allReviewed) { Text("Save confirmed plan") }
        if (!allReviewed) Text("Review or remove every generated item before confirming.", style = MaterialTheme.typography.bodySmall)
    }
    if (confirmDialog) AlertDialog(onDismissRequest = { confirmDialog = false }, title = { Text("Confirm this plan?") }, text = { Text("CareBinder does not verify medical instructions. Check the original and contact the clinician with questions.") }, confirmButton = { TextButton({ onConfirm(edited()); confirmDialog = false }) { Text("Confirm and save") } }, dismissButton = { TextButton({ confirmDialog = false }) { Text("Go back") } })
}

@Composable
private fun ScheduleEditor(value: ScheduleInput, onChange: (ScheduleInput) -> Unit) {
    val timingOptions = listOf("ALL_DAY" to "All day", "AT_TIME" to "At time", "TIME_RANGE" to "Range")
    val repeatOptions = listOf("NONE", "DAILY", "WEEKLY", "MONTHLY")
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Schedule", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(value.date, { revised -> onChange(value.copy(date = revised, endDate = if (value.endDate == value.date || value.endDate < revised) revised else value.endDate)) }, Modifier.fillMaxWidth(), label = { Text("Date (YYYY-MM-DD)") }, singleLine = true)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                timingOptions.forEach { (key, label) ->
                    if (value.timingMode == key) Button({ onChange(value.copy(timingMode = key)) }, Modifier.weight(1f)) { Text(label) }
                    else OutlinedButton({ onChange(value.copy(timingMode = key)) }, Modifier.weight(1f)) { Text(label) }
                }
            }
            if (value.timingMode != "ALL_DAY") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value.startTime, { onChange(value.copy(startTime = it)) }, Modifier.weight(1f), label = { Text("Start time") }, singleLine = true)
                    if (value.timingMode == "TIME_RANGE") OutlinedTextField(value.endTime, { onChange(value.copy(endTime = it)) }, Modifier.weight(1f), label = { Text("End time") }, singleLine = true)
                }
                if (value.timingMode == "TIME_RANGE") OutlinedTextField(value.endDate, { onChange(value.copy(endDate = it)) }, Modifier.fillMaxWidth(), label = { Text("End date (YYYY-MM-DD)") }, singleLine = true)
            }
            OutlinedButton({ val next = repeatOptions[(repeatOptions.indexOf(value.recurrenceFrequency) + 1) % repeatOptions.size]; onChange(value.copy(recurrenceFrequency = next, recurrenceUntil = if (next == "NONE") "" else value.recurrenceUntil)) }, Modifier.fillMaxWidth()) {
                Text(if (value.recurrenceFrequency == "NONE") "Does not repeat" else "Repeats ${value.recurrenceFrequency.lowercase()}")
            }
            if (value.recurrenceFrequency != "NONE") OutlinedTextField(value.recurrenceUntil, { onChange(value.copy(recurrenceUntil = it)) }, Modifier.fillMaxWidth(), label = { Text("Repeat until (optional)") }, singleLine = true)
        }
    }
}

@Composable
private fun AppearanceEditor(iconKey: String, colorKey: String, onChange: (String, String) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(ui("Event appearance"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Choose a small icon and one of 16 colors.", style = MaterialTheme.typography.bodySmall)
            eventIcons.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { (key, glyph) ->
                        if (iconKey == key) Button({ onChange(key, colorKey) }, Modifier.weight(1f)) { Text("$glyph ${key.take(3)}") }
                        else OutlinedButton({ onChange(key, colorKey) }, Modifier.weight(1f)) { Text("$glyph ${key.take(3)}") }
                    }
                }
            }
            eventColors.entries.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { (key, color) ->
                        if (colorKey == key) Button({ onChange(iconKey, key) }, Modifier.weight(1f)) { Text("● ${key.take(3)}", color = color) }
                        else OutlinedButton({ onChange(iconKey, key) }, Modifier.weight(1f)) { Text("● ${key.take(3)}", color = color) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventEditScreen(event: UiEvent, onBack: () -> Unit, onSave: (UiEvent) -> Unit, modifier: Modifier = Modifier) {
    var summary by remember(event.id) { mutableStateOf(event.summary) }
    var update by remember(event.id) { mutableStateOf(event.familyUpdate) }
    var schedule by remember(event.id) { mutableStateOf(event.toScheduleInput()) }
    var iconKey by remember(event.id) { mutableStateOf(event.iconKey) }; var colorKey by remember(event.id) { mutableStateOf(event.colorKey) }
    ScreenColumn(modifier) {
        TextButton(onBack) { Text("Back to timeline") }
        Text(ui("Edit care event"), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Changes update the confirmed plan and keep the original source attached.")
        OutlinedTextField(summary, { summary = it }, Modifier.fillMaxWidth(), label = { Text("Event summary") }, minLines = 2)
        ScheduleEditor(schedule, { schedule = it })
        AppearanceEditor(iconKey, colorKey) { icon, color -> iconKey = icon; colorKey = color }
        OutlinedTextField(update, { update = it }, Modifier.fillMaxWidth(), label = { Text("Family update") }, minLines = 3)
        Button({ onSave(event.withSchedule(schedule).copy(summary = summary, familyUpdate = update, iconKey = iconKey, colorKey = colorKey)) }, Modifier.fillMaxWidth(), enabled = summary.isNotBlank()) { Text("Save event changes") }
    }
}

@Composable
private fun UpdatesScreen(events: List<UiEvent>, modifier: Modifier = Modifier) {
    val context = LocalContext.current; val latest = events.firstOrNull(); var reviewed by remember(latest?.id) { mutableStateOf(false) }; var update by remember(latest?.id) { mutableStateOf(latest?.displayFamilyUpdate.orEmpty()) }
    ScreenColumn(modifier) {
        Text("Family updates", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        if (latest == null) EmptyCard("No approved update yet", "Confirm a care event first, then review exactly what you want to share.")
        else { OutlinedTextField(update, { update = it; reviewed = false }, Modifier.fillMaxWidth(), label = { Text("Update to share") }, minLines = 5); Row { Checkbox(reviewed, { reviewed = it }); Text("I reviewed exactly what will be shared", Modifier.padding(top = 12.dp)) }; Button({ context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, update) }, "Share family update")) }, Modifier.fillMaxWidth(), enabled = reviewed) { Text("Share update") }; Text("CareBinder opens the system share sheet and never sends automatically.", style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun ProfileScreen(recipient: Recipient, language: String, onLanguage: (String) -> Unit, onSave: (String, String) -> Unit, onExport: () -> Unit, onSignOut: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    var name by remember(recipient.id) { mutableStateOf(recipient.displayName) }; var relationship by remember(recipient.id) { mutableStateOf(recipient.relationship) }; var deleting by remember { mutableStateOf(false) }
    ScreenColumn(modifier) {
        Text("Care profile", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Preferred name") }); OutlinedTextField(relationship, { relationship = it }, Modifier.fillMaxWidth(), label = { Text("Your relationship") })
        Button({ onSave(name.trim(), relationship.trim()) }, Modifier.fillMaxWidth(), enabled = name.isNotBlank() && relationship.isNotBlank()) { Text("Save profile") }
        HorizontalDivider(); Text(ui("Language"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        listOf("en" to "English", "ru" to "Russian", "es" to "Spanish").forEach { (key, label) -> if (language == key) Button({ onLanguage(key) }, Modifier.fillMaxWidth()) { Text(ui(label)) } else OutlinedButton({ onLanguage(key) }, Modifier.fillMaxWidth()) { Text(ui(label)) } }
        Text("Original content stays on the server. AI translations are temporary and may contain errors.", style = MaterialTheme.typography.bodySmall)
        HorizontalDivider(); Text("Privacy and data", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold); Text(PrivacyBoundary.HEALTH_DISCLAIMER)
        OutlinedButton(onExport, Modifier.fillMaxWidth()) { Text("Export confirmed plans") }; OutlinedButton(onSignOut, Modifier.fillMaxWidth()) { Text("Sign out") }; TextButton({ deleting = true }) { Text("Delete account", color = MaterialTheme.colorScheme.error) }
    }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("Delete this account?") }, text = { Text("This permanently removes the profile, sources, drafts, events, tasks, and sessions from this backend.") }, confirmButton = { TextButton({ onDelete(); deleting = false }) { Text("Delete account", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton({ deleting = false }) { Text("Cancel") } })
}

@Composable
private fun TaskCard(task: UiTask, onChecked: () -> Unit, onReminder: () -> Unit, onEdit: () -> Unit) {
    val overdue = isTaskOverdue(task)
    Card(Modifier.fillMaxWidth(), colors = if (overdue) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer) else CardDefaults.cardColors()) { Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Checkbox(task.isComplete, { onChecked() }); Column(Modifier.weight(1f)) { if (overdue) Text(ui("Past due"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold); Text(task.displayTitle, fontWeight = FontWeight.SemiBold); Text(task.dueDate?.let(::formatDate) ?: "No due date", style = MaterialTheme.typography.bodySmall); task.reminderAt?.let { Text("Reminder: $it", style = MaterialTheme.typography.bodySmall) }; Text("From: ${task.displaySourceText}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }; Column { TextButton(onEdit) { Text("Edit") }; TextButton(onReminder) { Text("Reminder") } } } }
}

@Composable
private fun EventSummaryCard(event: UiEvent, overdue: Boolean, onEdit: (() -> Unit)?) {
    val eventColor = eventColors[event.colorKey] ?: eventColors.getValue("teal")
    val glyph = eventIcons.firstOrNull { it.first == event.iconKey }?.second ?: "✎"
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (overdue) MaterialTheme.colorScheme.errorContainer else eventColor.copy(alpha = .12f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(ui(if (overdue) "Past due" else "Confirmed"), color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold); Text(formatSchedule(event), style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Surface(shape = MaterialTheme.shapes.small, color = eventColor.copy(alpha = .18f)) { Text(glyph, Modifier.padding(horizontal = 10.dp, vertical = 7.dp), color = eventColor, fontWeight = FontWeight.Bold) }; Text(event.displaySummary, Modifier.weight(1f), fontWeight = FontWeight.SemiBold) }
            Text("${event.tasks.count { !it.isComplete }} open of ${event.tasks.size} tasks", style = MaterialTheme.typography.bodySmall)
            onEdit?.let { OutlinedButton(it) { Text("Edit event") } }
        }
    }
}

@Composable
private fun DraftSummaryCard(event: UiEvent, onReview: () -> Unit) {
    val eventColor = eventColors[event.colorKey] ?: eventColors.getValue("teal")
    val glyph = eventIcons.firstOrNull { it.first == event.iconKey }?.second ?: "✎"
    Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(shape = MaterialTheme.shapes.small, color = eventColor.copy(alpha = .18f)) { Text(glyph, Modifier.padding(horizontal = 10.dp, vertical = 7.dp), color = eventColor, fontWeight = FontWeight.Bold) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text("Needs review", color = MaterialTheme.colorScheme.tertiary); Text(event.displaySummary, maxLines = 2, overflow = TextOverflow.Ellipsis); OutlinedButton(onReview) { Text("Continue review") } }
    } }
}

@Composable
private fun TaskEditDialog(task: UiTask, onDismiss: () -> Unit, onSave: (String, String?, String?) -> Unit) {
    var title by remember(task.id) { mutableStateOf(task.title) }
    var dueDate by remember(task.id) { mutableStateOf(task.dueDate.orEmpty()) }
    var reminder by remember(task.id) { mutableStateOf(task.reminderAt?.let(::isoToLocalDateTime).orEmpty()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Edit task") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(title, { title = it }, label = { Text("Task title") }); OutlinedTextField(dueDate, { dueDate = it }, label = { Text("Due date") }); OutlinedTextField(reminder, { reminder = it }, label = { Text("Reminder (YYYY-MM-DDTHH:mm)") }); Text("Original: ${task.sourceText}", style = MaterialTheme.typography.bodySmall) } }, confirmButton = { TextButton({ onSave(title.trim(), dueDate.trim().ifBlank { null }, reminder.trim().ifBlank { null }?.let(::localDateTimeToIso)) }, enabled = title.isNotBlank()) { Text("Save") } }, dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

@Composable private fun EmptyCard(title: String, detail: String) = Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold); Text(detail) } }

@Composable private fun ScreenColumn(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) = Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)

private fun formatDate(value: String): String = runCatching { LocalDate.parse(value).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }.getOrDefault(value)

private fun isoToLocalDateTime(value: String): String = runCatching { Instant.parse(value).atZone(ZoneId.systemDefault()).toLocalDateTime().toString().take(16) }.getOrDefault(value.take(16))
private fun localDateTimeToIso(value: String): String = runCatching { LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant().toString() }.getOrDefault(value)

private fun UiEvent.toScheduleInput(): ScheduleInput = ScheduleInput(
    date = date,
    timingMode = timingMode,
    startTime = startsAt?.let(::isoToLocalDateTime)?.substringAfter('T') ?: "09:00",
    endDate = endsAt?.let(::isoToLocalDateTime)?.substringBefore('T') ?: date,
    endTime = endsAt?.let(::isoToLocalDateTime)?.substringAfter('T') ?: "10:00",
    recurrenceFrequency = recurrenceFrequency,
    recurrenceUntil = recurrenceUntil.orEmpty(),
)

private fun UiEvent.withSchedule(schedule: ScheduleInput): UiEvent {
    val starts = if (schedule.timingMode == "ALL_DAY") null else localDateTimeToIso("${schedule.date}T${schedule.startTime}")
    val ends = if (schedule.timingMode == "TIME_RANGE") localDateTimeToIso("${schedule.endDate}T${schedule.endTime}") else null
    return copy(date = schedule.date, timingMode = schedule.timingMode, startsAt = starts, endsAt = ends, recurrenceFrequency = schedule.recurrenceFrequency, recurrenceUntil = schedule.recurrenceUntil.ifBlank { null })
}

private fun formatSchedule(event: UiEvent): String {
    val base = if (event.timingMode == "ALL_DAY" || event.startsAt == null) formatDate(event.date) else runCatching {
        Instant.parse(event.startsAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))
    }.getOrDefault(event.startsAt)
    val range = if (event.timingMode == "TIME_RANGE" && event.endsAt != null) runCatching {
        val start = Instant.parse(event.startsAt).atZone(ZoneId.systemDefault()); val end = Instant.parse(event.endsAt).atZone(ZoneId.systemDefault())
        "–" + end.format(DateTimeFormatter.ofPattern(if (start.toLocalDate() == end.toLocalDate()) "h:mm a" else "MMM d, h:mm a"))
    }.getOrDefault("") else ""
    val repeat = if (event.recurrenceFrequency == "NONE") "" else " · ${event.recurrenceFrequency.lowercase()}${event.recurrenceUntil?.let { " until ${formatDate(it)}" }.orEmpty()}"
    return base + range + repeat
}

private fun isTaskOverdue(task: UiTask): Boolean = !task.isComplete && task.dueDate?.let { runCatching { LocalDate.parse(it).isBefore(LocalDate.now()) }.getOrDefault(false) } == true

private fun startVoiceRecording(cacheDirectory: File, onStarted: (MediaRecorder, File) -> Unit, onError: (String) -> Unit) {
    val outputFile = File.createTempFile("carebinder_voice_", ".m4a", cacheDirectory); val mediaRecorder = MediaRecorder()
    try { mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC); mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC); mediaRecorder.setOutputFile(outputFile.absolutePath); mediaRecorder.prepare(); mediaRecorder.start(); onStarted(mediaRecorder, outputFile) }
    catch (_: Exception) { mediaRecorder.release(); outputFile.delete(); onError("Voice recording is unavailable right now.") }
}
