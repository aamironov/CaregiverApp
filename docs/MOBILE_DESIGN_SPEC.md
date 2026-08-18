# CareBinder mobile MVP design specification

## 1. Purpose

CareBinder helps an adult child turn paperwork and notes from a care event into a caregiver-confirmed plan. The product must feel calm, plain spoken, and trustworthy—not clinical, alarming, or overly automated.

**Primary user:** an adult child coordinating care for an older parent while managing work and family.

**Core outcome:** after a visit, a caregiver can capture the source material, understand the next steps, correct the draft, save the confirmed plan, and send a family update in under five minutes.

## 2. Product rules that shape the experience

| Rule | UI consequence |
| --- | --- |
| CareBinder organizes information; it does not provide medical advice. | Persistent, compact safety language in capture and review; never use diagnostic, urgent, or prescriptive phrasing. |
| AI output is a draft. | Every generated item is visibly marked **Needs review** until the caregiver confirms it. |
| Nothing is saved, scheduled, or shared automatically. | Confirmation is an explicit step before saving actions or opening the share sheet. |
| The caregiver must be able to verify a draft. | Each extracted item can reveal its source excerpt and source document/page. |
| One care recipient in MVP. | Setup creates one profile; navigation names that person, with no people switcher. |

## 3. Information architecture

Use a five-item bottom navigation after setup:

| Destination | Purpose | Primary content |
| --- | --- | --- |
| Today | What needs attention now | Open tasks, due-date groups, reminder status, quick add event |
| Timeline | Find prior care events | Confirmed events, original sources, approved summaries |
| Add | Start a care event | Document, voice, or typed-note capture |
| Updates | Prepare a family message | Most recent approved event and saved updates |
| Profile | Manage the care recipient and app | Recipient information, privacy, export/delete entry points |

On small screens, the centered **Add** action may be a raised primary button rather than a normal tab. The active destination is always labelled with both icon and text.

## 4. Visual direction

The visual system should reduce cognitive load when the user may be tired or stressed.

- **Tone:** warm, steady, and practical. Avoid hospital-blue interfaces, medical iconography, sirens, or “AI magic” treatment.
- **Color:** a soft off-white canvas; dark charcoal text; a muted evergreen/teal primary for confirmed actions; amber only for review-needed status; red only for destructive actions or errors. Never use color as the sole status signal.
- **Typography:** system sans serif with a large, highly legible hierarchy. Body text is at least 16sp; tap labels never below 14sp; support 200% font scaling without clipping key actions.
- **Layout:** 16dp page gutters, 12–16dp card padding, 8dp spacing rhythm, 48dp minimum control height, and 44dp minimum touch targets. Use full-width primary actions near the bottom of the content area.
- **Components:** rounded but restrained surfaces (12dp radius), clear section headings, status chips with an icon, checkboxes for tasks, and expandable source-evidence rows. Keep shadows subtle; use borders to distinguish interactive cards.
- **Motion:** short, optional transitions that explain state changes. Respect reduced-motion settings. Processing uses an honest progress state, never a simulated percentage or claim of clinical verification.

## 5. Key flows and screens

### A. First-run setup

**Goal:** create the single care-recipient profile with minimal friction.

1. Welcome: “Keep the next steps from getting lost.” Explain that CareBinder turns *your* documents and notes into an editable plan.
2. Trust note: “You review every detail before anything is saved or shared.” Link to privacy details.
3. Profile form: preferred name (required) and relationship (required, selectable or free text).
4. Completion lands on the empty Today state.

The welcome screen must not imply medical expertise, data sharing, or automatic family collaboration.

### B. Today

**Goal:** surface the next useful action in seconds.

Header: “Today” with a supporting line such as “For Maya.” Lead with one primary action: **Add a care event**.

Task groups appear in this order: Overdue, Due today, Upcoming, No due date. A task row includes a completion checkbox, task title, due date in plain language, and a source-event label. Tapping the row opens task detail/source evidence; checking complete gives an undo snackbar.

Empty state: “No open tasks yet. Add paperwork or a note after the next visit, then review the plan together.” CTA: **Add a care event**.

### C. Add a care event

**Goal:** make source selection obvious while setting correct expectations.

Present three equal, large choices:

- **Document** — take photos or select a PDF
- **Voice note** — record a caregiver note
- **Typed note** — enter instructions manually

Below the choices: “CareBinder creates an editable draft from what you provide. Review the original instructions before you save or share.” A secondary link opens the product-safety explanation.

#### Document capture

Use a guided capture surface with page thumbnails, “Add page,” “Retake,” and “Continue.” Show quality guidance before upload (good light, whole page visible) without blocking a usable image. Selected PDFs display file name, page count, and remove affordance.

#### Voice and typed notes

Voice capture shows elapsed time, pause/resume, playback, rerecord, and a transcript-availability state. Typed notes use a multiline field, character count only when useful, and optional event date. Neither screen should suggest that the app verified spoken or typed information.

### D. Processing

**Goal:** preserve trust while source material becomes a draft.

Show the source thumbnail/type and the message: “Creating a draft for you to review.” Explain that the draft is not saved or shared yet. Allow the user to leave; the pending draft remains visible as **Ready to review** when processing completes. A failure preserves the source and offers **Try again** and **Create a note manually**.

### E. Review draft (the required gate)

**Goal:** make correction and confirmation faster than accepting blindly.

Use a vertically scrollable review screen with a sticky footer. At the top, show source type/date and a review banner: “Check this against the original before confirming.” Include **View source**.

Sections, in this exact order:

1. **Event summary** — editable text; label it “Draft summary.”
2. **Tasks and deadlines** — one card per task with title, optional date, edit, delete, and **Show source**. Newly generated items show an amber “Needs review” chip. Add-task affordance is always available.
3. **Medication items from the source** — copied wording only, editable, with source evidence. No dosage interpretation, timing recommendation, or safety claim.
4. **Questions for the clinician** — editable prompts. Phrase as questions to ask, never answers or recommendations.
5. **Family update** — editable plain-language message with a character-friendly preview.

The footer has **Save confirmed plan** as the primary action and **Save as draft** as secondary. The primary action stays disabled until every generated task and medication item is individually marked reviewed, edited, or removed. Before save, show a concise confirmation sheet:

> You’re confirming the details you want to save. CareBinder does not verify medical instructions—check the original document and contact the clinician with questions.

Actions: **Go back** and **Confirm and save**. After save, a success screen summarizes number of tasks saved and offers **Set reminders** and **Prepare family update**. The product must not create reminders by default.

### F. Task detail and reminders

**Goal:** keep an action traceable and controllable.

Task detail exposes title, completion status, due date, optional reminder, event link, and source excerpt. Editing the reminder is explicit: date/time, notification preference, save. A task with no date can still be saved; do not pressure the user to invent a deadline.

### G. Timeline

**Goal:** provide a quiet record of confirmed care events.

Show reverse-chronological event cards with date, source type, approved summary, task count/status, and a discreet “Confirmed” label. Draft events are separated into a “Needs review” section and never mingle with confirmed history. Event detail links to saved source documents and confirmed content, with the same source-evidence presentation used in Review.

### H. Family update and system share

**Goal:** give the caregiver ownership of what leaves the app.

Only approved updates are available here. Start with the editable update text, event date, and source-event name. The primary CTA is **Review before sharing**; the next screen shows exactly what will be sent and a checkbox: “I reviewed this update.” Only then enable **Share update**, which invokes Android’s system share sheet. Do not expose recipients, send automatically, or retain a recipient list in MVP.

## 6. States, validation, and recovery

| Situation | Required behavior |
| --- | --- |
| Low-confidence extraction or conflicting source text | Highlight the item, show its source, and require a manual decision: edit, keep, or remove. |
| Missing/unclear due date | Display “No due date”; allow the caregiver to add one. |
| Review interrupted | Save the extraction as a local/remote draft with a prominent **Continue review** entry. |
| Upload or extraction failure | Keep the original source; provide retry and manual-note alternatives; use plain language. |
| Unsaved edits | Warn only when leaving a changed review or share screen; offer Keep editing / Discard changes. |
| Deletion | Use a separate destructive confirmation that clearly names the item and impact. |
| No network | Clearly state that capture can be kept locally as a draft and that processing requires a connection. |

## 7. Accessibility and content requirements

- Meet WCAG 2.2 AA contrast; support screen readers with semantic control labels and announced save/error states.
- Never rely on swipe-only or long-press-only controls. Make task completion, editing, deletion, and evidence viewing available as labelled buttons.
- Use date formatting such as “Tomorrow, Aug 13” and retain the absolute date in detail views.
- Use source-respecting language: “copied from your document,” “draft,” “review,” “confirm,” and “question for the clinician.” Avoid “prescription,” “diagnosis,” “safe,” “urgent,” “should,” and “recommended” unless verbatim in source material and visually identified as a quote.
- Provide in-product access to privacy, export, and deletion. Never put raw documents, recipient names, medication content, or extracted text into analytics or crash logs.

## 8. Acceptance criteria for MVP design

The design is ready for implementation when it demonstrably supports these outcomes:

1. A first-time caregiver can create a profile and start a document, voice, or typed care event.
2. Every AI-generated item can be edited, deleted, or traced to source evidence before confirmation.
3. A generated task or medication item cannot become a saved plan item without a recorded review decision.
4. Reminders and shares require a separate, deliberate caregiver action after confirmation.
5. Today distinguishes open work from history; Timeline distinguishes drafts from confirmed events.
6. The primary path remains usable one-handed on a small Android phone, at large text sizes, and with a screen reader.
7. All safety, privacy, offline, error, empty, and interruption states above have designed copy and actions—not developer-only fallbacks.

## 9. Measurement events (privacy-safe)

Measure only product interaction events with no source text, care-recipient names, document content, or medication details:

- `profile_created`
- `event_capture_started` / `event_capture_completed` (source type only)
- `draft_ready` / `draft_review_started` / `draft_confirmed`
- `draft_item_edited`, `draft_item_removed`, `source_evidence_viewed` (item category only)
- `reminder_created`
- `family_update_shared`
- `event_deleted` / `account_deletion_started`

Use the metrics in the product plan to evaluate the flow: capture-to-confirmation completion, time to first plan, edit rate, update-share rate, and 30-day return.
