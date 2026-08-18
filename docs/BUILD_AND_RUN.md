# Build and run the CareBinder Android MVP

## What runs today

The Android Compose application connects to the Java + SQLite CareBinder API. It supports:

- Google sign-in through Credential Manager, email/password fallback, session restoration, and sign-out;
- one persistent care-recipient profile;
- camera document photos, selected image/PDF/audio/text files, direct voice recording, and typed notes;
- protected source upload with Bytez image/PDF extraction and speech transcription when configured;
- persisted drafts with all-day, exact-time, multi-day time-range, and daily/weekly/monthly recurring schedules;
- generated source-type icons and a selectable 16-color palette for drafts and confirmed events;
- English, Russian, and Spanish interface/content preferences with transient AI translation;
- confirmed event and task editing, plus overdue events and tasks pinned to the top in red;
- explicit accepted/edited/removed review decisions before server confirmation;
- Today, Timeline, local notifications, task completion, confirmed sharing, export, and account deletion; and
- encrypted offline caching of the last synchronized profile, drafts, and all events; and
- system light/dark appearance.

The backend uses Bytez to extract source wording, then converts that wording into deterministic, reviewable task suggestions. It does not perform clinical inference, and no suggested task is confirmed automatically. See [BACKEND_RUN.md](BACKEND_RUN.md) for the server-only Bytez configuration.

## Prerequisites

- JDK 17 or 21
- Android SDK Platform 35 and Build Tools 35
- An Android 8.0+ emulator or device
- The Java backend running on port 8080; see [BACKEND_RUN.md](BACKEND_RUN.md)

## Build

For Google sign-in, register an Android OAuth client for package `com.familycare.carebinder` and the signing certificate SHA-1 in the same Google Cloud project as the Web OAuth client. Credential Manager requests an ID token for the **Web client ID**, which must also appear in the backend’s `GOOGLE_CLIENT_IDS` allowlist.

Get the debug SHA-1 with `./gradlew signingReport`, then build with:

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
./gradlew :app:testDebugUnitTest :app:assembleDebug \
  -PCAREBINDER_GOOGLE_WEB_CLIENT_ID="your-web-client-id.apps.googleusercontent.com"
```

If the Gradle property is omitted, the Google button is hidden and email/password authentication remains available. The client ID is public configuration, not a secret; never place an OAuth client secret in the Android app.

No credential is needed for offline caching. Android encrypts the last synchronized snapshot with an app-specific AES key held by Android Keystore. After one successful authenticated synchronization, the user can reopen and read all cached events without a network connection. Server mutations, translation, and synchronization remain online-only for this MVP. Signing out or deleting the account removes the local snapshot.

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

The default debug API URL is `http://10.0.2.2:8080`, which reaches the host machine from the Android emulator. For a physical device or deployed HTTPS API, change `CAREBINDER_API_BASE_URL` in `app/build.gradle.kts` before building. Cleartext traffic is enabled only to support this local MVP setup and must be disabled for production.

## Acceptance check

1. Start the Java backend and open the Android app.
2. Sign in with Google (and separately verify the email/password fallback), then create a single care profile.
3. Add a typed event: `Call the clinic next week. Bring the medication list.` Set it to a multi-day time range and weekly recurrence.
4. Edit one task, set an optional due date/reminder, and mark every retained task reviewed.
5. Choose an icon and one of the 16 event colors, confirm the plan, then edit its summary, appearance, time range, and recurrence from Today or Timeline.
6. Give an open task a past due date and verify its event is pinned to the top in red; complete and reopen it to verify state persists.
7. In Updates, verify Share is disabled until “I reviewed exactly what will be shared” is checked.
8. Export confirmed plans from Profile.
9. Delete the account and verify the previous token can no longer access the profile.
10. With `BYTEZ_API_KEY` configured, switch between English, Russian, and Spanish; verify interface localization and transient translation of synthetic document, speech, event, and task text while editing continues to show the canonical source wording.
11. Complete one online synchronization, disconnect the emulator, relaunch the app, and verify the offline banner plus the complete cached event timeline. Reconnect before making changes.

## Distribution boundary

Do not distribute the local HTTP build for real health information. A beta build requires HTTPS, encrypted storage, production identity/account recovery, private deployment, tested backup/deletion operations, and the gates in [PRIVACY_AND_SAFETY.md](PRIVACY_AND_SAFETY.md).
