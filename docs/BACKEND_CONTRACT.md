# CareBinder backend contract

This contract is deliberately provider-neutral. It can be implemented with Firebase, Cloud Run, or another authenticated backend without changing the Android workflow. It protects the core rule: generated health-related content is a draft until a caregiver confirms it.

## Authentication and data rules

- Every request requires an end-user access token. The backend derives the user ID from that token; it never accepts a user ID supplied by the client.
- All event, source, draft, export, and deletion routes enforce care-recipient ownership.
- Raw files, extracted text, recipient names, medication items, and AI prompts/responses must not be written to application logs, analytics, or error reporting.
- The mobile app receives only short-lived upload URLs. AI-provider credentials stay server-side.

## Endpoints

| Method and route | Request | Response | Rules |
| --- | --- | --- | --- |
| `GET /v1/auth/config` | — | `{ "googleEnabled", "googleClientId?", "bytezEnabled" }` | Exposes public feature configuration only; never return a client secret. |
| `POST /v1/auth/register` | `{ "email", "password" }` | `{ "accessToken", "expiresAt", "user" }` | Hash password with PBKDF2; return a 30-day bearer session. |
| `POST /v1/auth/login` | `{ "email", "password" }` | Session | Use a generic invalid-credentials response. |
| `POST /v1/auth/google` | `{ "credential": "Google ID token" }` | Session | Verify signature, issuer, audience, expiry, subject, and verified email server-side. Auto-link existing accounts only for authoritative Gmail/Workspace email. |
| `POST /v1/auth/logout` | — | `204` | Revokes the current bearer session. |
| `GET /v1/settings` | — | `{ "language", "translationEnabled" }` | Returns the caller's language preference (`en`, `ru`, or `es`) and public provider availability. |
| `PATCH /v1/settings` | `{ "language" }` | Settings | Persists only a supported language key. |
| `POST /v1/translations` | `{ "texts": string[] }` | `{ "language", "translationEnabled", "translations" }` | Translates up to 40 items through the server-side AI provider; does not persist translations or replace originals. |
| `POST /v1/care-recipients` | `{ "displayName", "relationship" }` | `CareRecipient` | MVP permits one active recipient per account. |
| `GET /v1/care-recipients/me` | — | `CareRecipient` | Returns the caller’s active profile. |
| `PATCH /v1/care-recipients/me` | `{ "displayName", "relationship" }` | `CareRecipient` | Updates only the caller’s profile. |
| `POST /v1/events/uploads` | `{ "recipientId", "contentType", "filename" }` | `{ "assetId", "uploadUrl", "expiresAt" }` | Authorize before issuing a private, short-lived upload URL. |
| `POST /v1/events/drafts` | Source plus `EventSchedule` | `ExtractionDraft` | Validate ownership; extract uploaded image/PDF/audio through the server-side Bytez client; return a reviewable draft only. Never create confirmed tasks, reminders, or shares here. |
| `GET /v1/drafts` | — | `ExtractionDraft[]` | Returns the caller’s unconfirmed drafts. |
| `PUT /v1/drafts/{draftId}` | Edited draft fields | `ExtractionDraft` | Generated item IDs must remain complete; removals are explicit state. |
| `DELETE /v1/drafts/{draftId}` | — | `204` | Deletes only the caller’s draft. |
| `POST /v1/events/confirm` | `ConfirmedEventPayload` | `CareEvent` | Reject every item that lacks an explicit caregiver review decision. |
| `GET /v1/events?recipientId=` | — | `CareEvent[]` | Return confirmed events and separate saved drafts. |
| `GET /v1/events/{eventId}` | — | `CareEvent` | Includes source references only for the event owner. |
| `PATCH /v1/events/{eventId}` | Editable summary, family update, and `EventSchedule` | `CareEvent` | Enforces ownership and validates dates, time order, and recurrence. |
| `PATCH /v1/tasks/{taskId}` | `{ "completed?", "reminderAt?", "title?", "dueDate?" }` | `CareTask` | Enforces event ownership. |
| `POST /v1/exports` | `{ "recipientId", "format" }` | `{ "downloadUrl", "expiresAt" }` | Exports confirmed content only. |
| `DELETE /v1/account` | `{ "confirmation": "DELETE" }` | `202 Accepted` | Deletes sources, drafts, events, reminders, and account data; provide status polling if asynchronous. |

## Draft schema

`EventSchedule` supports all-day events, a precise start, or a start/end range that may span multiple days:

```json
{
  "occurredOn": "2026-08-20",
  "timingMode": "TIME_RANGE",
  "startsAt": "2026-08-20T14:00:00Z",
  "endsAt": "2026-08-21T15:00:00Z",
  "recurrenceFrequency": "WEEKLY",
  "recurrenceInterval": 1,
  "recurrenceUntil": "2026-10-01"
}
```

Allowed timing modes are `ALL_DAY`, `AT_TIME`, and `TIME_RANGE`. Allowed recurrence frequencies are `NONE`, `DAILY`, `WEEKLY`, and `MONTHLY`. Exact times are stored as UTC instants; clients display them in the device timezone. An event is overdue when it has unfinished work and its non-recurring end/start/date has passed, when its recurrence has ended, or when it contains an unfinished task with a past due date.

Event appearance uses portable semantic keys. Allowed `iconKey` values are `note`, `document`, `voice`, `medical`, `calendar`, `meal`, `sleep`, and `activity`. Allowed `colorKey` values are `slate`, `gray`, `red`, `orange`, `amber`, `yellow`, `lime`, `green`, `emerald`, `teal`, `cyan`, `sky`, `blue`, `indigo`, `violet`, and `pink`. Clients own the exact glyph and theme-aware color rendering; the backend validates and persists the keys.

```json
{
  "draftId": "draft_123",
  "recipientId": "recipient_123",
  "iconKey": "document",
  "colorKey": "violet",
  "sourceExtraction": { "kind": "BYTEZ_DOCUMENT", "model": "configured-model-id", "requiresReview": true },
  "eventSummary": {
    "text": "Follow-up instructions from the visit.",
    "sourceReferences": [{ "assetId": "asset_123", "excerpt": "..." }]
  },
  "tasks": [
    {
      "id": "task_123",
      "title": "Schedule follow-up",
      "dueDate": "2026-08-19",
      "sourceReferences": [{ "assetId": "asset_123", "excerpt": "..." }]
    }
  ],
  "medicationItems": [],
  "questionsForClinician": [],
  "familyUpdate": "A caregiver-editable update.",
  "warnings": []
}
```

The extraction service must return empty lists when the source does not support an item. It must not infer diagnoses, clinical urgency, treatment, medication dosage, or medication safety.

## Confirmation payload

`POST /v1/events/confirm` accepts only a caregiver-edited representation of the draft:

```json
{
  "draftId": "draft_123",
  "recipientId": "recipient_123",
  "eventSummary": "Edited by caregiver",
  "items": [
    {
      "draftItemId": "task_123",
      "decision": "accepted",
      "title": "Schedule follow-up",
      "dueDate": "2026-08-19"
    }
  ],
  "familyUpdate": "Edited by caregiver"
}
```

Allowed `decision` values are `accepted`, `edited`, and `removed`. The server rejects items omitted from the payload or supplied with an invalid decision; this preserves an auditable confirmation gate even if a client is modified or compromised.

## Error shape

```json
{
  "code": "SOURCE_UNREADABLE",
  "message": "We could not read that source. Keep the original and add a note manually.",
  "retryable": true
}
```

Use stable error codes such as `UNAUTHENTICATED`, `FORBIDDEN`, `SOURCE_UNREADABLE`, `BYTEZ_NOT_CONFIGURED`, `SOURCE_EXTRACTION_FAILED`, `DRAFT_EXPIRED`, and `VALIDATION_ERROR`. Error messages must never echo raw sensitive content.

## Verification checklist

Before connecting the Android client, integration tests must prove that forged Google tokens are rejected, verified identities create/link the intended account, one user cannot access another user’s source, draft, event, export, or delete route; a draft cannot create a reminder/share/event without confirmation; source upload URLs expire; and account deletion removes every asset and record.
