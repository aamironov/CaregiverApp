const state = {
  token: localStorage.getItem("carebinder.token"),
  recipient: null,
  events: [],
  drafts: [],
  page: "today",
  authMode: "login",
  authConfig: {googleEnabled: false, bytezEnabled: false},
  language: localStorage.getItem("carebinder.language") || "en",
  translationEnabled: false,
  translations: new Map(),
  offline: false,
  reviewDraft: null,
  editEvent: null,
  editTask: null,
  theme: localStorage.getItem("carebinder.theme") || "system"
};

const navItems = [
  ["today", "⌂", "Today"], ["timeline", "◷", "Timeline"], ["add", "+", "Add"],
  ["updates", "↗", "Updates"], ["profile", "○", "Profile"]
];

const eventIcons = {note: ["✎", "Note"], document: ["▤", "Document"], voice: ["◉", "Voice"], medical: ["+", "Care"], calendar: ["◷", "Appointment"], meal: ["♨", "Meal"], sleep: ["☾", "Sleep"], activity: ["☆", "Activity"]};
const eventColors = {slate: "#64748b", gray: "#78716c", red: "#dc2626", orange: "#ea580c", amber: "#d97706", yellow: "#ca8a04", lime: "#65a30d", green: "#16a34a", emerald: "#059669", teal: "#0d9488", cyan: "#0891b2", sky: "#0284c7", blue: "#2563eb", indigo: "#4f46e5", violet: "#7c3aed", pink: "#db2777"};
const interfaceTranslations = {
  ru: {"Today":"Сегодня","Timeline":"Хронология","Add":"Добавить","Updates":"Обновления","Profile":"Профиль","Add a care event":"Добавить событие ухода","Scheduled events":"Запланированные события","Open tasks":"Открытые задачи","Past due":"Просрочено","Ready to review":"Готово к проверке","Needs review":"Требует проверки","Continue review":"Продолжить проверку","Edit event":"Изменить событие","Create draft to review":"Создать черновик","Review draft":"Проверить черновик","Event summary":"Описание события","Tasks and deadlines":"Задачи и сроки","Family update":"Семейное обновление","Save as draft":"Сохранить черновик","Save confirmed plan":"Сохранить подтверждённый план","Event appearance":"Внешний вид события","Choose a small icon and one of 16 colors.":"Выберите значок и один из 16 цветов.","Schedule":"Расписание","Source type":"Тип источника","Optional caregiver note":"Необязательная заметка","Language":"Язык","Interface and content language":"Язык интерфейса и содержимого","Save language":"Сохранить язык","Privacy and data":"Конфиденциальность и данные","Sign out":"Выйти","Delete my account":"Удалить мой аккаунт","English":"Английский","Russian":"Русский","Spanish":"Испанский","Confirmed":"Подтверждено","Edit care event":"Изменить событие ухода","Save event changes":"Сохранить изменения"},
  es: {"Today":"Hoy","Timeline":"Cronología","Add":"Añadir","Updates":"Actualizaciones","Profile":"Perfil","Add a care event":"Añadir evento de cuidado","Scheduled events":"Eventos programados","Open tasks":"Tareas abiertas","Past due":"Atrasado","Ready to review":"Listo para revisar","Needs review":"Requiere revisión","Continue review":"Continuar revisión","Edit event":"Editar evento","Create draft to review":"Crear borrador para revisar","Review draft":"Revisar borrador","Event summary":"Resumen del evento","Tasks and deadlines":"Tareas y fechas límite","Family update":"Actualización familiar","Save as draft":"Guardar borrador","Save confirmed plan":"Guardar plan confirmado","Event appearance":"Apariencia del evento","Choose a small icon and one of 16 colors.":"Elige un icono y uno de 16 colores.","Schedule":"Horario","Source type":"Tipo de fuente","Optional caregiver note":"Nota opcional del cuidador","Language":"Idioma","Interface and content language":"Idioma de interfaz y contenido","Save language":"Guardar idioma","Privacy and data":"Privacidad y datos","Sign out":"Cerrar sesión","Delete my account":"Eliminar mi cuenta","English":"Inglés","Russian":"Ruso","Spanish":"Español","Confirmed":"Confirmado","Edit care event":"Editar evento de cuidado","Save event changes":"Guardar cambios"}
};

const $ = selector => document.querySelector(selector);
const escapeHtml = value => String(value ?? "").replace(/[&<>'"]/g, character => ({"&":"&amp;","<":"&lt;",">":"&gt;","'":"&#39;",'"':"&quot;"}[character]));
const activeLocale = () => ({en: "en-US", ru: "ru-RU", es: "es-ES"})[state.language] || "en-US";
const formatDate = value => value ? new Intl.DateTimeFormat(activeLocale(), {dateStyle: "medium"}).format(new Date(`${value}T12:00:00`)) : "No due date";
const translated = value => state.translations.get(value) || value;

function localizeDom() {
  document.documentElement.lang = state.language;
  const dictionary = interfaceTranslations[state.language] || {};
  const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
  while (walker.nextNode()) {
    const node = walker.currentNode, trimmed = node.nodeValue.trim(), replacement = dictionary[trimmed];
    if (replacement) node.nodeValue = node.nodeValue.replace(trimmed, replacement);
  }
}

async function api(path, options = {}) {
  const headers = new Headers(options.headers || {});
  if (state.token) headers.set("Authorization", `Bearer ${state.token}`);
  if (options.body && !(options.body instanceof Blob) && !(options.body instanceof ArrayBuffer)) headers.set("Content-Type", "application/json");
  const response = await fetch(path, {...options, headers});
  if (response.status === 204) return null;
  if (!response.ok) {
    const error = await response.json().catch(() => ({message: "Something went wrong."}));
    if (response.status === 401 && !path.includes("/auth/")) signOut();
    const failure = new Error(error.message || "Something went wrong."); failure.status = response.status; throw failure;
  }
  if ((response.headers.get("content-type") || "").includes("application/json")) return response.json();
  return response.blob();
}

function offlineDb() {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open("carebinder-offline", 1);
    request.onupgradeneeded = () => request.result.createObjectStore("secure");
    request.onsuccess = () => resolve(request.result); request.onerror = () => reject(request.error);
  });
}

async function offlineValue(key, value) {
  const db = await offlineDb();
  return new Promise((resolve, reject) => {
    const transaction = db.transaction("secure", value === undefined ? "readonly" : "readwrite");
    const request = value === undefined ? transaction.objectStore("secure").get(key) : transaction.objectStore("secure").put(value, key);
    request.onsuccess = () => resolve(request.result); request.onerror = () => reject(request.error);
  });
}

async function offlineKey() {
  let key = await offlineValue("key");
  if (!key) { key = await crypto.subtle.generateKey({name: "AES-GCM", length: 256}, false, ["encrypt", "decrypt"]); await offlineValue("key", key); }
  return key;
}

async function saveOfflineSnapshot() {
  if (!state.recipient) return;
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const data = new TextEncoder().encode(JSON.stringify({recipient: state.recipient, events: state.events, drafts: state.drafts, language: state.language, savedAt: new Date().toISOString()}));
  const payload = await crypto.subtle.encrypt({name: "AES-GCM", iv}, await offlineKey(), data);
  await offlineValue("snapshot", {iv, payload});
}

async function restoreOfflineSnapshot() {
  try {
    const cached = await offlineValue("snapshot"); if (!cached) return false;
    const plain = await crypto.subtle.decrypt({name: "AES-GCM", iv: cached.iv}, await offlineKey(), cached.payload);
    const snapshot = JSON.parse(new TextDecoder().decode(plain));
    state.recipient = snapshot.recipient; state.events = snapshot.events || []; state.drafts = snapshot.drafts || []; state.language = snapshot.language || "en"; state.offline = true; state.translations = new Map();
    return true;
  } catch (_) { return false; }
}

async function clearOfflineSnapshot() { await offlineValue("snapshot", null); }

function toast(message) {
  const element = $("#toast");
  element.textContent = message;
  element.classList.add("show");
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => element.classList.remove("show"), 2800);
}

function applyTheme() {
  const root = document.documentElement;
  if (state.theme === "system") root.removeAttribute("data-theme"); else root.dataset.theme = state.theme;
  const label = state.theme[0].toUpperCase() + state.theme.slice(1);
  $("#theme-button").textContent = `Appearance: ${label}`;
}

function cycleTheme() {
  state.theme = ({system: "light", light: "dark", dark: "system"})[state.theme];
  localStorage.setItem("carebinder.theme", state.theme);
  applyTheme();
  if (!$("#auth-view").classList.contains("hidden")) initializeGoogleSignIn();
}

function setupNav() {
  const markup = navItems.map(([id, icon, label]) => `<button class="nav-button ${state.page === id ? "active" : ""}" data-page="${id}"><span class="nav-icon">${icon}</span><span>${label}</span></button>`).join("");
  $("#desktop-nav").innerHTML = markup;
  $("#mobile-nav").innerHTML = markup;
  document.querySelectorAll("[data-page]").forEach(button => button.addEventListener("click", () => navigate(button.dataset.page)));
}

function navigate(page) {
  state.page = page;
  state.reviewDraft = null;
  state.editEvent = null;
  state.editTask = null;
  setupNav();
  render();
  $("#page").focus();
}

function setTitle(title, context = state.recipient ? `For ${state.recipient.displayName}` : "Care plan") {
  $("#page-title").textContent = title;
  $("#context-label").textContent = context;
}

async function bootstrap() {
  applyTheme();
  try { state.authConfig = await api("/v1/auth/config"); } catch (_) { state.authConfig = {googleEnabled: false, bytezEnabled: false}; }
  if (!state.token) return showAuth();
  try {
    await loadData();
    showApp();
  } catch (error) {
    if (error.message.includes("Create a care profile")) {
      state.recipient = null;
      showApp();
      state.page = "profile";
      render();
    } else if (error.status === 401) {
      signOut();
    } else if (await restoreOfflineSnapshot()) {
      showApp(); toast("Offline — showing the last encrypted event snapshot.");
    } else {
      signOut();
    }
  }
}

async function loadData() {
  const settings = await api("/v1/settings");
  state.language = settings.language || "en"; state.translationEnabled = settings.translationEnabled;
  localStorage.setItem("carebinder.language", state.language);
  state.recipient = await api("/v1/care-recipients/me");
  [state.events, state.drafts] = await Promise.all([api(`/v1/events?recipientId=${encodeURIComponent(state.recipient.id)}`), api("/v1/drafts")]);
  await translateLoadedContent();
  state.offline = false;
  await saveOfflineSnapshot();
}

async function translateLoadedContent() {
  state.translations = new Map();
  if (state.language === "en" || !state.translationEnabled) return;
  const values = [...new Set([...state.events, ...state.drafts].flatMap(event => [event.eventSummary, event.familyUpdate, ...event.tasks.flatMap(task => [task.title, task.sourceText])]).filter(Boolean))].slice(0, 40);
  if (!values.length) return;
  try {
    const result = await api("/v1/translations", {method: "POST", body: JSON.stringify({texts: values})});
    values.forEach((value, index) => state.translations.set(value, result.translations[index] || value));
  } catch (_) { state.translations = new Map(); }
}

function showAuth() {
  $("#auth-view").classList.remove("hidden");
  $("#app-view").classList.add("hidden");
  initializeGoogleSignIn();
  queueMicrotask(localizeDom);
}

function showApp() {
  $("#auth-view").classList.add("hidden");
  $("#app-view").classList.remove("hidden");
  setupNav();
  render();
}

function signOut() {
  if (state.token) api("/v1/auth/logout", {method: "POST"}).catch(() => {});
  state.token = null; state.recipient = null; state.events = []; state.drafts = [];
  localStorage.removeItem("carebinder.token");
  clearOfflineSnapshot().catch(() => {});
  if (window.google?.accounts?.id) window.google.accounts.id.disableAutoSelect();
  showAuth();
}

async function acceptSession(session) {
  state.token = session.accessToken;
  localStorage.setItem("carebinder.token", state.token);
  try { await loadData(); } catch (loadError) { if (!loadError.message.includes("Create a care profile")) throw loadError; }
  showApp();
  if (!state.recipient) navigate("profile");
}

let googleScriptPromise;
function loadGoogleIdentityServices() {
  if (window.google?.accounts?.id) return Promise.resolve();
  if (!googleScriptPromise) googleScriptPromise = new Promise((resolve, reject) => {
    const script = document.createElement("script");
    script.src = "https://accounts.google.com/gsi/client";
    script.async = true;
    script.onload = resolve;
    script.onerror = () => reject(new Error("Google sign-in could not be loaded."));
    document.head.appendChild(script);
  });
  return googleScriptPromise;
}

async function initializeGoogleSignIn() {
  const container = $("#google-auth");
  if (!state.authConfig.googleEnabled || !state.authConfig.googleClientId) return container.classList.add("hidden");
  container.classList.remove("hidden");
  try {
    await loadGoogleIdentityServices();
    window.google.accounts.id.initialize({
      client_id: state.authConfig.googleClientId,
      callback: async response => {
        const error = $("#auth-error");
        error.textContent = "";
        try {
          await acceptSession(await api("/v1/auth/google", {method: "POST", body: JSON.stringify({credential: response.credential})}));
        } catch (caught) { error.textContent = caught.message; }
      }
    });
    const button = $("#google-signin-button");
    button.replaceChildren();
    window.google.accounts.id.renderButton(button, {
      type: "standard", shape: "rectangular", theme: state.theme === "dark" ? "filled_black" : "outline",
      text: "continue_with", size: "large", width: Math.min(360, Math.max(240, button.parentElement.clientWidth - 8))
    });
  } catch (caught) { $("#auth-error").textContent = caught.message; }
}

function setAuthMode(mode) {
  state.authMode = mode;
  $("#login-tab").classList.toggle("active", mode === "login");
  $("#register-tab").classList.toggle("active", mode === "register");
  $("#auth-title").textContent = mode === "login" ? "Welcome back" : "Create your private workspace";
  $("#auth-submit").textContent = mode === "login" ? "Sign in" : "Create account";
  $("#auth-password").autocomplete = mode === "login" ? "current-password" : "new-password";
  $("#auth-error").textContent = "";
}

async function submitAuth(event) {
  event.preventDefault();
  const error = $("#auth-error");
  error.textContent = "";
  try {
    const session = await api(`/v1/auth/${state.authMode}`, {method: "POST", body: JSON.stringify({email: $("#auth-email").value, password: $("#auth-password").value})});
    await acceptSession(session);
  } catch (caught) { error.textContent = caught.message; }
}

function render() {
  queueMicrotask(localizeDom);
  if (!state.recipient && state.page !== "profile") state.page = "profile";
  setupNav();
  if (state.reviewDraft) return renderReview();
  if (state.editEvent) return renderEditEvent();
  if (state.editTask) return renderEditTask();
  ({today: renderToday, timeline: renderTimeline, add: renderAdd, updates: renderUpdates, profile: renderProfile}[state.page] || renderToday)();
}

function renderToday() {
  setTitle("Today");
  const overdueEvents = state.events.filter(event => event.overdue);
  const upcomingEvents = state.events.filter(event => !event.overdue).sort((a, b) => scheduleSortValue(a).localeCompare(scheduleSortValue(b)));
  const tasks = state.events.flatMap(event => event.tasks.map(task => ({...task, event}))).filter(item => !item.completed).sort((a, b) => Number(isTaskOverdue(b)) - Number(isTaskOverdue(a)));
  $("#page").innerHTML = `<div class="stack">
    ${state.offline ? `<div class="notice offline-notice"><strong>Offline — cached events</strong><span>Changes require an internet connection.</span></div>` : ""}
    ${overdueEvents.length ? `<div class="section-head overdue-heading"><div><p class="eyebrow">Needs attention</p><h2>Past due</h2><p>Unfinished events and tasks are kept at the top.</p></div></div>${overdueEvents.map(event => eventCard(event, true)).join("")}` : ""}
    <div class="section-head"><div><h2>Scheduled events</h2><p>${upcomingEvents.length ? `${upcomingEvents.length} upcoming or recurring event${upcomingEvents.length === 1 ? "" : "s"}.` : "No upcoming events."}</p></div></div>
    ${upcomingEvents.length ? upcomingEvents.map(event => eventCard(event)).join("") : emptyCard("No scheduled events", "Add an all-day, timed, or recurring care event.", "Add a care event", "add")}
    <div class="section-head"><div><h2>Open tasks</h2><p>${tasks.length ? `${tasks.length} next step${tasks.length === 1 ? "" : "s"} from confirmed plans.` : "Nothing needs attention right now."}</p></div></div>
    ${tasks.length ? tasks.map(taskCard).join("") : emptyCard("No open tasks yet", "Add paperwork or a note after the next visit, then review the plan together.", "Add a care event", "add")}
    ${state.drafts.length ? `<div class="section-head"><div><h2>Ready to review</h2><p>Drafts are not part of the confirmed plan yet.</p></div></div>${state.drafts.map(draftCard).join("")}` : ""}
  </div>`;
  bindCommon();
}

function taskCard(item) {
  const task = item.task || item;
  const event = item.event || {};
  const overdue = isTaskOverdue(task);
  return `<article class="card task ${task.completed ? "done" : ""} ${overdue ? "overdue" : ""}">
    <input type="checkbox" aria-label="Mark ${escapeHtml(task.title)} complete" data-task-complete="${task.id}" ${task.completed ? "checked" : ""}>
    <div><div class="status-row">${overdue ? `<span class="chip overdue-chip">Past due</span>` : ""}<h3>${escapeHtml(translated(task.title))}</h3></div><p>${escapeHtml(formatDate(task.dueDate))}${task.reminderAt ? ` · Reminder ${escapeHtml(new Date(task.reminderAt).toLocaleString())}` : ""}</p><div class="source">From ${escapeHtml(event.occurredOn || "confirmed plan")}: ${escapeHtml(translated(task.sourceText))}</div></div>
    <div class="task-actions"><button class="link-button" data-edit-task="${task.id}" type="button">Edit</button><button class="link-button" data-reminder="${task.id}" type="button">Reminder</button></div>
  </article>`;
}

function eventCard(event, overdue = event.overdue) {
  const icon = eventIcons[event.iconKey] || eventIcons.note;
  const color = eventColors[event.colorKey] || eventColors.teal;
  return `<article class="card event-card ${overdue ? "overdue" : ""}" style="--event-color:${color}"><div class="card-row"><div class="event-identity"><span class="event-icon" role="img" aria-label="${escapeHtml(icon[1])}">${icon[0]}</span><div><div class="status-row">${overdue ? `<span class="chip overdue-chip">Past due</span>` : `<span class="chip confirmed">Confirmed</span>`}<span class="schedule-summary">${escapeHtml(formatSchedule(event))}</span></div><h3>${escapeHtml(translated(event.eventSummary))}</h3><p>${event.tasks.filter(task => !task.completed).length} open of ${event.tasks.length} task${event.tasks.length === 1 ? "" : "s"}</p></div></div><button class="secondary compact" data-edit-event="${event.id}" type="button">Edit event</button></div></article>`;
}

function draftCard(draft) {
  const icon = eventIcons[draft.iconKey] || eventIcons.note;
  return `<article class="card card-row"><div class="event-identity"><span class="event-icon" style="--event-color:${eventColors[draft.colorKey] || eventColors.teal}" role="img" aria-label="${escapeHtml(icon[1])}">${icon[0]}</span><div><span class="chip review">Needs review</span><h3>${escapeHtml(translated(draft.eventSummary))}</h3><p>${draft.tasks.length} suggested task${draft.tasks.length === 1 ? "" : "s"}</p></div></div><button class="secondary" data-review="${draft.draftId}" type="button">Continue review</button></article>`;
}

function emptyCard(title, detail, action, page) {
  return `<article class="card empty"><h2>${escapeHtml(title)}</h2><p>${escapeHtml(detail)}</p>${action ? `<button class="primary" data-go="${page}" type="button">${escapeHtml(action)}</button>` : ""}</article>`;
}

function renderTimeline() {
  setTitle("Timeline");
  $("#page").innerHTML = `<div class="stack">
    ${state.drafts.length ? `<div class="section-head"><div><h2>Needs review</h2><p>These drafts are saved but not confirmed.</p></div></div>${state.drafts.map(draftCard).join("")}` : ""}
    <div class="section-head"><div><h2>Confirmed care events</h2><p>A quiet record of caregiver-approved plans.</p></div></div>
    ${state.events.length ? state.events.map(event => `${eventCard(event)}${event.tasks.map(task => `<div class="timeline-task ${isTaskOverdue(task) ? "past-due-text" : ""}">${task.completed ? "Completed" : "Open"} · ${escapeHtml(translated(task.title))}</div>`).join("")}`).join("") : emptyCard("No confirmed events", "Your reviewed plans will appear here.")}
  </div>`;
  bindCommon();
}

function renderAdd() {
  setTitle("Add a care event", "Create a reviewable draft");
  $("#page").innerHTML = `<div class="stack">
    <div class="notice"><strong>Review before you save.</strong> CareBinder creates suggestions from what you provide. Check every item against the original source.</div>
    <form id="add-form" class="card">
      <div class="field-grid"><label>Source type<select id="source-type"><option value="TYPED_NOTE">Typed note</option><option value="DOCUMENT">Document or PDF</option><option value="VOICE_NOTE">Voice note or recording</option></select></label></div>
      ${scheduleFields({}, "add")}
      ${appearanceFields({}, "add")}
      <label class="source-picker"><input id="source-file" type="file" accept="image/*,.pdf,audio/*,text/plain"><span><strong>Choose an optional source file</strong><span class="helper">Images, PDF, audio, or text up to 10 MB</span></span></label>
      <p id="file-name" class="helper">No source file selected.</p>
      <label>Optional caregiver note<textarea id="source-note" placeholder="Add context Bytez may not find in the source."></textarea></label>
      <p class="helper">${state.authConfig.bytezEnabled ? "Bytez extracts text from images and PDFs and transcribes audio. The extracted wording becomes suggested tasks for your review." : "Automatic extraction needs BYTEZ_API_KEY on the backend. Until then, add a note or transcript here."}</p>
      <p class="form-error" id="add-error" role="alert"></p>
      <button class="primary" type="submit">Create draft to review</button>
    </form>
  </div>`;
  $("#source-file").addEventListener("change", event => {
    const file = event.target.files[0];
    $("#file-name").textContent = file?.name || "No source file selected.";
    if (file) $("#source-type").value = file.type.startsWith("audio/") ? "VOICE_NOTE" : file.type.startsWith("text/") ? "TYPED_NOTE" : "DOCUMENT";
    setGeneratedIcon($("#source-type").value);
  });
  $("#source-type").addEventListener("change", event => setGeneratedIcon(event.target.value));
  bindScheduleFields("add");
  $("#add-form").addEventListener("submit", submitAdd);
}

function setGeneratedIcon(sourceType) {
  const key = sourceType === "DOCUMENT" ? "document" : sourceType === "VOICE_NOTE" ? "voice" : "note";
  const input = document.querySelector(`input[name="add-icon"][value="${key}"]`);
  if (input) input.checked = true;
}

async function submitAdd(event) {
  event.preventDefault();
  const error = $("#add-error"); error.textContent = "";
  const submit = event.submitter || $("#add-form button[type=submit]");
  submit.disabled = true; submit.textContent = "Extracting and creating draft…";
  try {
    const file = $("#source-file").files[0];
    let assetId = null;
    if (file) {
      const upload = await api("/v1/events/uploads", {method: "POST", body: JSON.stringify({recipientId: state.recipient.id, contentType: file.type || "application/octet-stream", filename: file.name})});
      await api(upload.uploadUrl, {method: "PUT", headers: {"Content-Type": file.type || "application/octet-stream"}, body: file});
      assetId = upload.assetId;
    }
    const draft = await api("/v1/events/drafts", {method: "POST", body: JSON.stringify({recipientId: state.recipient.id, sourceType: $("#source-type").value, assetId, typedNote: $("#source-note").value, ...readSchedule("add"), ...readAppearance("add")})});
    state.drafts.unshift(draft); state.reviewDraft = draft; await saveOfflineSnapshot(); render();
  } catch (caught) { error.textContent = caught.message; submit.disabled = false; submit.textContent = "Create draft to review"; }
}

function renderReview() {
  const draft = state.reviewDraft;
  setTitle("Review draft", "Nothing is confirmed yet");
  $("#page").innerHTML = `<form id="review-form" class="stack">
    <div class="notice"><strong>Check this against the original.</strong> ${draft.sourceExtraction?.kind === "BYTEZ_SPEECH" ? "Bytez transcribed the recording and converted its wording into suggestions." : draft.sourceExtraction?.kind === "BYTEZ_DOCUMENT" ? "Bytez extracted the document wording and converted it into suggestions." : "Every generated item needs your decision before it can enter the confirmed plan."}</div>
    <section class="card stack"><h2>Event summary</h2><label>Draft summary<textarea id="review-summary">${escapeHtml(draft.eventSummary)}</textarea></label>${scheduleFields(draft, "review")}${appearanceFields(draft, "review")}</section>
    <div class="section-head"><div><h2>Tasks and deadlines</h2><p>Edit, remove, and mark each suggestion reviewed.</p></div></div>
    <div id="review-tasks" class="stack">${draft.tasks.filter(task => !task.removed).map(reviewItem).join("")}</div>
    <section class="card stack"><h2>Questions for the clinician</h2><div id="questions">${draft.questionsForClinician.map((question, index) => `<label>Question ${index + 1}<input data-question="${index}" value="${escapeHtml(question)}"></label>`).join("")}</div><button class="secondary" id="add-question" type="button">Add question</button></section>
    <section class="card stack"><h2>Family update</h2><label>Approved wording<textarea id="review-update">${escapeHtml(draft.familyUpdate)}</textarea></label></section>
    <p class="form-error" id="review-error" role="alert"></p>
    <div class="sticky-actions"><button class="secondary" id="save-draft" type="button">Save as draft</button><button class="primary" id="confirm-plan" type="submit">Save confirmed plan</button></div>
  </form>`;
  $("#review-form").addEventListener("submit", confirmPlan);
  $("#save-draft").addEventListener("click", saveCurrentDraft);
  $("#add-question").addEventListener("click", () => { syncReview(); draft.questionsForClinician.push(""); renderReview(); });
  bindScheduleFields("review");
  const updateConfirmState = () => { $("#confirm-plan").disabled = [...document.querySelectorAll("[data-reviewed]")].some(input => !input.checked); };
  document.querySelectorAll("[data-reviewed]").forEach(input => input.addEventListener("change", updateConfirmState));
  updateConfirmState();
  document.querySelectorAll("[data-remove-item]").forEach(button => button.addEventListener("click", () => { const task = draft.tasks.find(item => item.id === button.dataset.removeItem); if (task) task.removed = true; renderReview(); }));
}

function reviewItem(task) {
  return `<article class="card review-item" data-review-item="${task.id}">
    <div class="field-grid"><label>Task<input data-title value="${escapeHtml(task.title)}"></label><label>Due date (optional)<input data-due type="date" value="${escapeHtml(task.dueDate || "")}"></label></div>
    <div class="source">Source: ${escapeHtml(task.sourceText)}</div>
    <div class="review-actions"><label class="review-check"><input data-reviewed type="checkbox" ${task.reviewed ? "checked" : ""}>I reviewed this item</label><button class="link-button" data-remove-item="${task.id}" type="button">Remove</button></div>
  </article>`;
}

function syncReview() {
  const draft = state.reviewDraft;
  draft.eventSummary = $("#review-summary")?.value ?? draft.eventSummary;
  draft.familyUpdate = $("#review-update")?.value ?? draft.familyUpdate;
  Object.assign(draft, readSchedule("review"));
  Object.assign(draft, readAppearance("review"));
  document.querySelectorAll("[data-review-item]").forEach(element => {
    const task = draft.tasks.find(item => item.id === element.dataset.reviewItem);
    if (task) { task.title = element.querySelector("[data-title]").value; task.dueDate = element.querySelector("[data-due]").value || null; task.reviewed = element.querySelector("[data-reviewed]").checked; }
  });
  draft.questionsForClinician = [...document.querySelectorAll("[data-question]")].map(input => input.value).filter(Boolean);
}

async function saveCurrentDraft() {
  syncReview();
  try {
    const saved = await api(`/v1/drafts/${state.reviewDraft.draftId}`, {method: "PUT", body: JSON.stringify(state.reviewDraft)});
    state.drafts = state.drafts.filter(draft => draft.draftId !== saved.draftId); state.drafts.unshift(saved); state.reviewDraft = null; await saveOfflineSnapshot(); toast("Draft saved."); navigate("timeline");
  } catch (caught) { $("#review-error").textContent = caught.message; }
}

async function confirmPlan(event) {
  event.preventDefault(); syncReview();
  const draft = state.reviewDraft;
  if (draft.tasks.some(task => !task.removed && !task.reviewed)) { $("#review-error").textContent = "Review or remove every suggested task before confirming."; return; }
  try {
    const items = draft.tasks.map(task => ({draftItemId: task.id, decision: task.removed ? "removed" : task.title === task.sourceText ? "accepted" : "edited", title: task.title, dueDate: task.dueDate}));
    const confirmed = await api("/v1/events/confirm", {method: "POST", body: JSON.stringify({draftId: draft.draftId, recipientId: draft.recipientId, eventSummary: draft.eventSummary, familyUpdate: draft.familyUpdate, occurredOn: draft.occurredOn, timingMode: draft.timingMode, startsAt: draft.startsAt, endsAt: draft.endsAt, recurrenceFrequency: draft.recurrenceFrequency, recurrenceInterval: draft.recurrenceInterval, recurrenceUntil: draft.recurrenceUntil, iconKey: draft.iconKey, colorKey: draft.colorKey, questionsForClinician: draft.questionsForClinician, items})});
    state.events.unshift(confirmed); state.drafts = state.drafts.filter(item => item.draftId !== draft.draftId); state.reviewDraft = null; await saveOfflineSnapshot(); toast("Confirmed plan saved."); navigate("today");
  } catch (caught) { $("#review-error").textContent = caught.message; }
}

function localDateTimeValue(iso) {
  if (!iso) return "";
  const date = new Date(iso);
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 16);
}

function scheduleFields(item = {}, prefix) {
  const date = item.occurredOn || new Date().toISOString().slice(0, 10);
  const timing = item.timingMode || "ALL_DAY";
  const recurrence = item.recurrenceFrequency || "NONE";
  const start = localDateTimeValue(item.startsAt).slice(11) || "09:00";
  const endLocal = localDateTimeValue(item.endsAt);
  const endDate = endLocal.slice(0, 10) || date;
  const end = endLocal.slice(11) || "10:00";
  return `<div class="schedule-panel stack">
    <div class="field-grid three"><label>Date<input id="${prefix}-date" type="date" value="${escapeHtml(date)}" required></label><label>Timing<select id="${prefix}-timing"><option value="ALL_DAY" ${timing === "ALL_DAY" ? "selected" : ""}>All day</option><option value="AT_TIME" ${timing === "AT_TIME" ? "selected" : ""}>Exact time</option><option value="TIME_RANGE" ${timing === "TIME_RANGE" ? "selected" : ""}>Start and end</option></select></label><label>Repeats<select id="${prefix}-recurrence"><option value="NONE" ${recurrence === "NONE" ? "selected" : ""}>Does not repeat</option><option value="DAILY" ${recurrence === "DAILY" ? "selected" : ""}>Daily</option><option value="WEEKLY" ${recurrence === "WEEKLY" ? "selected" : ""}>Weekly</option><option value="MONTHLY" ${recurrence === "MONTHLY" ? "selected" : ""}>Monthly</option></select></label></div>
    <div id="${prefix}-time-fields" class="field-grid three"><label>Start time<input id="${prefix}-start-time" type="time" value="${escapeHtml(start)}"></label><label id="${prefix}-end-date-wrap">End date<input id="${prefix}-end-date" type="date" value="${escapeHtml(endDate)}" min="${escapeHtml(date)}"></label><label id="${prefix}-end-wrap">End time<input id="${prefix}-end-time" type="time" value="${escapeHtml(end)}"></label></div>
    <label id="${prefix}-until-wrap">Repeat until (optional)<input id="${prefix}-until" type="date" value="${escapeHtml(item.recurrenceUntil || "")}" min="${escapeHtml(date)}"></label>
  </div>`;
}

function bindScheduleFields(prefix) {
  const timing = $(`#${prefix}-timing`);
  const recurrence = $(`#${prefix}-recurrence`);
  const refresh = () => {
    $(`#${prefix}-time-fields`).classList.toggle("hidden", timing.value === "ALL_DAY");
    $(`#${prefix}-end-wrap`).classList.toggle("hidden", timing.value !== "TIME_RANGE");
    $(`#${prefix}-end-date-wrap`).classList.toggle("hidden", timing.value !== "TIME_RANGE");
    $(`#${prefix}-until-wrap`).classList.toggle("hidden", recurrence.value === "NONE");
    $(`#${prefix}-until`).min = $(`#${prefix}-date`).value;
    $(`#${prefix}-end-date`).min = $(`#${prefix}-date`).value;
    if ($(`#${prefix}-end-date`).value < $(`#${prefix}-date`).value) $(`#${prefix}-end-date`).value = $(`#${prefix}-date`).value;
  };
  timing.addEventListener("change", refresh); recurrence.addEventListener("change", refresh); $(`#${prefix}-date`).addEventListener("change", refresh); refresh();
}

function readSchedule(prefix) {
  const occurredOn = $(`#${prefix}-date`).value;
  const timingMode = $(`#${prefix}-timing`).value;
  const recurrenceFrequency = $(`#${prefix}-recurrence`).value;
  const startsAt = timingMode === "ALL_DAY" ? null : new Date(`${occurredOn}T${$(`#${prefix}-start-time`).value}`).toISOString();
  const endsAt = timingMode === "TIME_RANGE" ? new Date(`${$(`#${prefix}-end-date`).value}T${$(`#${prefix}-end-time`).value}`).toISOString() : null;
  return {occurredOn, timingMode, startsAt, endsAt, recurrenceFrequency, recurrenceInterval: 1, recurrenceUntil: recurrenceFrequency === "NONE" ? null : ($(`#${prefix}-until`).value || null)};
}

function appearanceFields(item = {}, prefix) {
  const selectedIcon = item.iconKey || "note";
  const selectedColor = item.colorKey || "teal";
  return `<fieldset class="appearance-panel"><legend>Event appearance</legend><p class="helper">Choose a small icon and one of 16 colors.</p><div class="icon-grid">${Object.entries(eventIcons).map(([key, [glyph, label]]) => `<label class="appearance-choice"><input type="radio" name="${prefix}-icon" value="${key}" ${key === selectedIcon ? "checked" : ""}><span class="event-icon" title="${label}">${glyph}</span><small>${label}</small></label>`).join("")}</div><div class="color-grid">${Object.entries(eventColors).map(([key, color]) => `<label class="color-choice" title="${key}"><input type="radio" name="${prefix}-color" value="${key}" ${key === selectedColor ? "checked" : ""}><span style="--swatch:${color}"></span><small>${key}</small></label>`).join("")}</div></fieldset>`;
}

function readAppearance(prefix) {
  return {iconKey: document.querySelector(`input[name="${prefix}-icon"]:checked`)?.value || "note", colorKey: document.querySelector(`input[name="${prefix}-color"]:checked`)?.value || "teal"};
}

function formatSchedule(event) {
  let value = event.timingMode === "ALL_DAY" || !event.startsAt
    ? formatDate(event.occurredOn)
    : new Intl.DateTimeFormat(activeLocale(), {dateStyle: "medium", timeStyle: "short"}).format(new Date(event.startsAt));
  if (event.timingMode === "TIME_RANGE" && event.endsAt) {
    const end = new Date(event.endsAt), start = new Date(event.startsAt);
    value += `–${new Intl.DateTimeFormat(activeLocale(), start.toDateString() === end.toDateString() ? {timeStyle: "short"} : {dateStyle: "medium", timeStyle: "short"}).format(end)}`;
  }
  if (event.recurrenceFrequency && event.recurrenceFrequency !== "NONE") value += ` · ${event.recurrenceFrequency.toLowerCase()}${event.recurrenceUntil ? ` until ${formatDate(event.recurrenceUntil)}` : ""}`;
  return value;
}

function scheduleSortValue(event) { return event.startsAt || `${event.occurredOn || "9999-12-31"}T00:00:00Z`; }
function isTaskOverdue(task) { return !task.completed && task.dueDate && task.dueDate < new Date().toISOString().slice(0, 10); }

function renderEditEvent() {
  const event = state.editEvent;
  setTitle("Edit care event", "Changes update the confirmed plan");
  $("#page").innerHTML = `<form id="edit-event-form" class="stack"><button id="cancel-event-edit" class="quiet" type="button">Back to timeline</button><section class="card stack"><h2>Event details</h2><label>Summary<textarea id="edit-event-summary" required>${escapeHtml(event.eventSummary)}</textarea></label>${scheduleFields(event, "edit-event")}${appearanceFields(event, "edit-event")}<label>Family update<textarea id="edit-event-update">${escapeHtml(event.familyUpdate)}</textarea></label><p class="form-error" id="edit-event-error"></p><button class="primary" type="submit">Save event changes</button></section></form>`;
  bindScheduleFields("edit-event");
  $("#cancel-event-edit").addEventListener("click", () => { state.editEvent = null; navigate("timeline"); });
  $("#edit-event-form").addEventListener("submit", async submitEvent => {
    submitEvent.preventDefault();
    try {
      await api(`/v1/events/${event.id}`, {method: "PATCH", body: JSON.stringify({eventSummary: $("#edit-event-summary").value, familyUpdate: $("#edit-event-update").value, ...readSchedule("edit-event"), ...readAppearance("edit-event")})});
      await loadData(); state.editEvent = null; toast("Event updated."); navigate("timeline");
    } catch (caught) { $("#edit-event-error").textContent = caught.message; }
  });
}

function renderEditTask() {
  const {task} = state.editTask;
  setTitle("Edit task", "Update the confirmed next step");
  $("#page").innerHTML = `<form id="edit-task-form" class="stack"><button id="cancel-task-edit" class="quiet" type="button">Back to Today</button><section class="card stack"><h2>Task details</h2><label>Task title<input id="edit-task-title" value="${escapeHtml(task.title)}" required></label><div class="field-grid"><label>Due date<input id="edit-task-due" type="date" value="${escapeHtml(task.dueDate || "")}"></label><label>Reminder<input id="edit-task-reminder" type="datetime-local" value="${escapeHtml(localDateTimeValue(task.reminderAt))}"></label></div><div class="source">Original source: ${escapeHtml(task.sourceText)}</div><p class="form-error" id="edit-task-error"></p><button class="primary" type="submit">Save task changes</button></section></form>`;
  $("#cancel-task-edit").addEventListener("click", () => { state.editTask = null; navigate("today"); });
  $("#edit-task-form").addEventListener("submit", async submitEvent => {
    submitEvent.preventDefault();
    const reminder = $("#edit-task-reminder").value;
    try {
      await api(`/v1/tasks/${task.id}`, {method: "PATCH", body: JSON.stringify({title: $("#edit-task-title").value, dueDate: $("#edit-task-due").value || null, reminderAt: reminder ? new Date(reminder).toISOString() : null})});
      await loadData(); state.editTask = null; toast("Task updated."); navigate("today");
    } catch (caught) { $("#edit-task-error").textContent = caught.message; }
  });
}

function renderUpdates() {
  setTitle("Family updates");
  const latest = state.events[0];
  $("#page").innerHTML = latest ? `<div class="stack"><section class="card stack"><span class="chip confirmed">Confirmed ${escapeHtml(formatDate(latest.occurredOn))}</span><label>Update to share<textarea id="share-text">${escapeHtml(latest.familyUpdate)}</textarea></label><label class="review-check"><input id="share-reviewed" type="checkbox">I reviewed exactly what will be copied</label><button id="copy-update" class="primary" type="button" disabled>Copy family update</button><p class="helper">CareBinder copies the text. You choose where and with whom to share it.</p></section></div>` : emptyCard("No approved update yet", "Confirm a care event first, then review exactly what you want to share.");
  if (latest) {
    $("#share-reviewed").addEventListener("change", event => $("#copy-update").disabled = !event.target.checked);
    $("#copy-update").addEventListener("click", async () => { await navigator.clipboard.writeText($("#share-text").value); toast("Family update copied."); });
  }
}

function renderProfile() {
  setTitle(state.recipient ? "Profile" : "Set up care profile", state.recipient ? "Account and privacy" : "One profile in the MVP");
  $("#page").innerHTML = `<div class="stack">
    <form id="profile-form" class="card stack"><h2>${state.recipient ? "Care recipient" : "Who are you helping?"}</h2><label>Preferred name<input id="profile-name" value="${escapeHtml(state.recipient?.displayName || "")}" required></label><label>Your relationship<input id="profile-relationship" value="${escapeHtml(state.recipient?.relationship || "")}" required></label><p class="form-error" id="profile-error"></p><button class="primary" type="submit">${state.recipient ? "Save profile" : "Create care profile"}</button></form>
    ${state.recipient ? `<form id="language-form" class="card stack"><h2>Language</h2><p>Interface and content language</p><label>Language<select id="language-select"><option value="en" ${state.language === "en" ? "selected" : ""}>English</option><option value="ru" ${state.language === "ru" ? "selected" : ""}>Russian</option><option value="es" ${state.language === "es" ? "selected" : ""}>Spanish</option></select></label><p class="helper">Original content remains on the server. AI translations are temporary and may contain errors.</p><button class="primary" type="submit">Save language</button></form>` : ""}
    ${state.recipient ? `<section class="card stack"><h2>Privacy and data</h2><p>Exports contain confirmed plans only. Drafts are excluded.</p><div class="button-row"><button id="export-button" class="secondary">Download confirmed plans</button><button id="signout-button" class="secondary">Sign out</button></div></section><section class="card stack danger-zone"><h2>Delete account</h2><p>Permanently removes the account, profile, sources, drafts, events, tasks, and sessions from this backend.</p><button id="delete-button" class="danger">Delete my account</button></section>` : ""}
  </div>`;
  $("#profile-form").addEventListener("submit", saveProfile);
  if (state.recipient) {
    $("#language-form").addEventListener("submit", saveLanguage);
    $("#signout-button").addEventListener("click", signOut);
    $("#export-button").addEventListener("click", downloadExport);
    $("#delete-button").addEventListener("click", deleteAccount);
  }
}

async function saveLanguage(event) {
  event.preventDefault();
  try {
    const settings = await api("/v1/settings", {method: "PATCH", body: JSON.stringify({language: $("#language-select").value})});
    state.language = settings.language; localStorage.setItem("carebinder.language", state.language);
    await loadData(); toast("Language updated."); render();
  } catch (caught) { toast(caught.message); }
}

async function saveProfile(event) {
  event.preventDefault();
  try {
    const body = JSON.stringify({displayName: $("#profile-name").value, relationship: $("#profile-relationship").value});
    state.recipient = await api(state.recipient ? "/v1/care-recipients/me" : "/v1/care-recipients", {method: state.recipient ? "PATCH" : "POST", body});
    await saveOfflineSnapshot();
    toast("Care profile saved."); navigate("today");
  } catch (caught) { $("#profile-error").textContent = caught.message; }
}

async function downloadExport() {
  try {
    const created = await api("/v1/exports", {method: "POST", body: JSON.stringify({recipientId: state.recipient.id, format: "text"})});
    const blob = await api(created.downloadUrl);
    const link = document.createElement("a"); link.href = URL.createObjectURL(blob); link.download = "carebinder-export.txt"; link.click(); URL.revokeObjectURL(link.href);
  } catch (caught) { toast(caught.message); }
}

function confirmDialog(title, message, action) {
  const dialog = $("#confirm-dialog"); $("#dialog-title").textContent = title; $("#dialog-message").textContent = message;
  dialog.showModal();
  dialog.addEventListener("close", () => { if (dialog.returnValue === "confirm") action(); }, {once: true});
}

function deleteAccount() {
  confirmDialog("Delete this account?", "This permanently removes all CareBinder data in this backend and cannot be undone.", async () => {
    try { await api("/v1/account", {method: "DELETE", body: JSON.stringify({confirmation: "DELETE"})}); state.token = null; localStorage.removeItem("carebinder.token"); await clearOfflineSnapshot(); showAuth(); toast("Account deleted."); } catch (caught) { toast(caught.message); }
  });
}

function bindCommon() {
  document.querySelectorAll("[data-go]").forEach(button => button.addEventListener("click", () => navigate(button.dataset.go)));
  document.querySelectorAll("[data-review]").forEach(button => button.addEventListener("click", () => { state.reviewDraft = state.drafts.find(draft => draft.draftId === button.dataset.review); render(); }));
  document.querySelectorAll("[data-edit-event]").forEach(button => button.addEventListener("click", () => { state.editEvent = state.events.find(event => event.id === button.dataset.editEvent); render(); }));
  document.querySelectorAll("[data-edit-task]").forEach(button => button.addEventListener("click", () => {
    for (const event of state.events) { const task = event.tasks.find(item => item.id === button.dataset.editTask); if (task) { state.editTask = {task, event}; break; } }
    render();
  }));
  document.querySelectorAll("[data-task-complete]").forEach(input => input.addEventListener("change", async () => {
    try { await api(`/v1/tasks/${input.dataset.taskComplete}`, {method: "PATCH", body: JSON.stringify({completed: input.checked})}); await loadData(); render(); toast(input.checked ? "Task completed." : "Task reopened."); } catch (caught) { toast(caught.message); }
  }));
  document.querySelectorAll("[data-reminder]").forEach(button => button.addEventListener("click", async () => {
    const value = prompt("Reminder date and time (YYYY-MM-DDTHH:mm), or leave blank to remove:");
    if (value === null) return;
    try { await api(`/v1/tasks/${button.dataset.reminder}`, {method: "PATCH", body: JSON.stringify({reminderAt: value ? new Date(value).toISOString() : null})}); await loadData(); render(); toast(value ? "Reminder saved." : "Reminder removed."); } catch (caught) { toast(caught.message); }
  }));
}

$("#login-tab").addEventListener("click", () => setAuthMode("login"));
$("#register-tab").addEventListener("click", () => setAuthMode("register"));
$("#auth-form").addEventListener("submit", submitAuth);
$("#add-button").addEventListener("click", () => navigate("add"));
$("#theme-button").addEventListener("click", cycleTheme);
bootstrap();
if ("serviceWorker" in navigator) navigator.serviceWorker.register("/sw.js").catch(() => {});
