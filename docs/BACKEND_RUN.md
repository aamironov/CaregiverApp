# Run the Java + SQLite MVP

## What is implemented

The `backend/` module is a Java 21 HTTP service with SQLite persistence. It serves both the JSON API and the responsive browser application.

Implemented MVP capabilities:

- Google ID-token authentication, optional email/password login with PBKDF2 hashing, and expiring CareBinder bearer sessions;
- one care-recipient profile per account;
- private, ownership-checked source uploads up to 10 MB;
- Bytez-backed image/PDF text extraction and audio transcription, followed by reviewable task suggestions;
- English, Russian, and Spanish preferences with server-only Bytez translation and original-only canonical storage;
- persisted reviewable drafts and server-enforced accepted/edited/removed decisions;
- generated compact event icons, a validated 16-color event palette, and caregiver appearance editing;
- all-day, exact-time, multi-day time-range, and recurring confirmed events with editable tasks, reminders, family updates, and source provenance;
- confirmed-plan text export and cascading account deletion; and
- responsive light/dark web UI for the complete caregiver workflow; and
- an offline-capable web shell plus an encrypted browser snapshot of the last synchronized profile, drafts, and events.

Bytez extracts source wording; the provider-neutral draft generator converts that wording into deterministic task suggestions. No suggestion becomes a saved task until the caregiver reviews and confirms it. Do not represent extraction or generated drafts as clinically verified.

## Prerequisites

- JDK 21
- Maven 3.9+

## Build and test

From `backend/`:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn -s settings.xml verify
```

The integration suite starts the real HTTP server on temporary ports and verifies password and Google authentication, safe account linking, user isolation, draft confirmation, multi-day/recurring schedules, event and task editing, overdue state, web assets, and cascading deletion.

## Configure Google sign-in

Google sign-in is disabled when no client ID is configured, so local email/password development continues to work unchanged.

1. In Google Auth Platform, configure the consent screen.
2. Create a **Web application** OAuth client. Add `http://localhost:8080` as an authorized JavaScript origin for local web testing. Use only HTTPS origins outside localhost.
3. Export the Web client ID before starting the backend. No OAuth client secret is used by this ID-token flow:

```bash
export GOOGLE_CLIENT_IDS="your-web-client-id.apps.googleusercontent.com"
```

`GOOGLE_CLIENT_IDS` accepts a comma-separated allowlist when more than one audience must be trusted. The backend verifies Google’s signature, issuer, audience, expiry, subject, and verified email, then issues its own revocable 30-day session. Gmail and Google Workspace identities may link to an existing password account with the same email because Google is authoritative for those addresses. Other third-party email identities are not auto-linked and must continue with password sign-in if that email already exists. The unique Google subject prevents one Google identity from linking to multiple accounts.

## Configure Bytez extraction

Create a Bytez API key, then configure it only on the backend:

```bash
export BYTEZ_API_KEY="your-bytez-key"
export BYTEZ_DOCUMENT_MODEL="Qwen/Qwen2.5-VL-7B-Instruct" # optional override
export BYTEZ_SPEECH_MODEL="openai/whisper-large-v3-turbo" # optional override
export BYTEZ_TRANSLATION_MODEL="Qwen/Qwen3-4B" # optional override
```

`BYTEZ_PROVIDER_KEY` is optional and is sent as Bytez's `provider-key` header when a selected closed-source model requires it. `BYTEZ_BASE_URL` can override the default `https://api.bytez.com/models/v2`, primarily for a compatible test endpoint. PDFs are rendered locally into page images and capped at 12 pages; images are transcribed by the document model, while audio is sent to the speech model as a base64 data URI. The service never logs source bytes, extracted text, or provider responses.

Without `BYTEZ_API_KEY`, typed notes and uploaded text files continue to work. Image, PDF, and audio drafts require either Bytez configuration or a caregiver-supplied note/transcript.

The language setting is stored as `en`, `ru`, or `es`. Fixed interface wording is localized on each client. User-generated summaries, task text, document extraction, and speech transcription remain canonical in their original form; clients request transient Bytez translations from `POST /v1/translations`. Translation failures fall back to the original instead of blocking access or overwriting content.

## Run locally

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export CAREBINDER_DB="$PWD/data/carebinder.db"
export PORT=8080
export GOOGLE_CLIENT_IDS="your-web-client-id.apps.googleusercontent.com" # optional
export BYTEZ_API_KEY="your-bytez-key" # optional; required for automatic document/audio extraction
java -jar target/carebinder-backend-0.1.0.jar
```

Open [http://localhost:8080](http://localhost:8080) for the web application. The Android emulator uses `http://10.0.2.2:8080` by default.

After the first authenticated synchronization, the browser encrypts the latest profile, drafts, and all events with a non-exportable Web Crypto key stored in IndexedDB. The service worker caches only the application shell. A returning user can read the last synchronized data without internet access; creating, editing, completing, translating, or synchronizing data still requires a connection. Signing out or deleting the account clears the encrypted browser snapshot.

SQLite creates the database and schema on first launch. Back up or remove the configured database file only when the service is stopped.

## Container

```bash
docker build -t carebinder-backend ./backend
docker run --rm -p 8080:8080 -e GOOGLE_CLIENT_IDS="your-web-client-id.apps.googleusercontent.com" -v carebinder-data:/data carebinder-backend
```

## MVP security boundary

This implementation is suitable for local development and an invite-only synthetic-data pilot. Before real health information or a public beta:

- terminate TLS in front of the service;
- encrypt the SQLite volume and backups at rest;
- restrict CORS to the production web origin;
- move source blobs to private object storage if usage grows;
- add rate limiting, password reset/email verification, operational monitoring without sensitive payloads, and backup/restore tests; and
- complete the release gates in [PRIVACY_AND_SAFETY.md](PRIVACY_AND_SAFETY.md).
