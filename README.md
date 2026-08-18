# CareBinder

CareBinder is a phone-first, consumer caregiving organizer. It turns appointment paperwork, discharge documents, and caregiver voice notes into an **editable** action plan, family update, and care timeline.

Current release: **0.0.1**.

## Product boundary

CareBinder organizes user-provided information. It is not a diagnostic, treatment, medication-safety, or emergency-response service. Every AI-created task, medication item, deadline, or summary must be shown to the caregiver for confirmation before it is saved, scheduled, or shared.

## Repository map

```text
app/                         Android Compose client for the shared MVP API
backend/                     Java 21 + SQLite API and responsive web application
docs/PRODUCT_PLAN.md         Product strategy, MVP scope, roadmap, and metrics
docs/CHILD_CARE_ROADMAP.md   Post-MVP feeding, sleep, and child-development plan
docs/ARCHITECTURE.md         System design, data flow, security boundaries, decisions
docs/PRIVACY_AND_SAFETY.md   Launch boundaries for the consumer beta
```

## MVP user journey

```text
Capture photo / PDF / voice note
  -> upload to protected backend
  -> extract structured draft
  -> caregiver verifies and edits
  -> save tasks and reminders
  -> share a caregiver-approved update
```

## First implementation order

1. Create the Android project from this Gradle skeleton in Android Studio.
2. Implement local-only capture and review screens with fake data.
3. Add authenticated backend upload and server-side extraction.
4. Add reminders, user deletion, and secure sharing.
5. Run a closed beta before enabling subscriptions.

See [the product plan](docs/PRODUCT_PLAN.md) and [architecture](docs/ARCHITECTURE.md) before adding features.

## Run the full MVP locally

The Java backend provides Google and email/password authentication, SQLite persistence, source upload, drafts, server-enforced confirmation, tasks/reminders, exports, deletion, and the responsive web UI. See [the backend guide](docs/BACKEND_RUN.md).

The Android app connects to that API and supports camera/file/audio capture, review, confirmed tasks, reminders, sharing, export, and deletion. See [the Android guide](docs/BUILD_AND_RUN.md).

When `BYTEZ_API_KEY` is configured on the backend, Bytez extracts text from document images/PDF pages, transcribes audio, and provides transient English/Russian/Spanish translations before the deterministic generator creates reviewable task suggestions. Canonical source text remains unchanged on the server, and typed notes remain available without Bytez. The local HTTP/SQLite setup is for synthetic-data testing, not a production health-data service.

After the first authenticated synchronization, Android and web clients retain an encrypted snapshot of the profile, drafts, and complete event/task timeline for offline reading. Offline caching needs no provider credential; server changes, AI extraction/translation, and synchronization require a connection. Local snapshots are removed on sign-out or account deletion.

For a Docker Compose VPS deployment with automatic HTTPS, server-only secrets, persistent SQLite storage, health checks, and backups, see [the VPS deployment guide](deploy/vps/README.md).

## Continuous build

GitHub Actions validates every push and pull request to `main`. The workflow runs the backend Maven test suite and the Android unit tests using Java 17. You can run the same checks locally:

```bash
cd backend && mvn verify
./gradlew testDebugUnitTest
```
