# CareBinder product plan

## Mission

Help a family turn the confusing paperwork after a medical visit into an understandable, caregiver-confirmed plan of action.

## First user

An adult child coordinating care for an older parent, balancing work and family, and needing to update siblings after appointments or discharge.

## MVP promise

"Take a photo after a medical visit. Know what happens next—and keep the family aligned."

## MVP scope

1. Create one care-recipient profile.
2. Capture a document photo/PDF, voice note, or typed note.
3. Produce an editable AI draft: event summary, tasks, deadlines, medication items copied from source material, clinician questions, and family update.
4. Require confirmation/editing before saving, reminders, or sharing.
5. Show Today (open tasks/reminders) and Timeline (past events/documents).
6. Copy/share a caregiver-approved update through Android sharing.

## Excluded from MVP

- Diagnosis, triage, treatment, medication interaction checking, and emergency alerts.
- EHR, provider portal, insurance, or pharmacy integrations.
- In-app family chat/live collaboration.
- Automatic appointment booking.
- Clinical/professional sales and HIPAA-compliance claims.

## Validation: weeks 1–2

Interview 15–20 caregivers about a specific recent appointment or discharge.

Ask:

- What paperwork or instructions came home?
- What was confusing?
- How did the family coordinate it?
- Which task was delayed, repeated, or lost?
- Would they upload a document photo if it produced an editable task list and update?

Pass condition: at least 10 interviewees independently describe the same post-visit coordination pain.

## Prototype: weeks 3–4

Build a clickable flow: care profile → add visit → document photos → AI draft → review → timeline → share update.

Test with 5–8 caregivers. Pass condition: 70% can complete the primary workflow without coaching and at least three agree to beta-test.

## Android alpha: weeks 5–8

Implement authenticated capture, protected upload, server-side structured extraction, editable review, task storage, reminders, sharing, and account deletion. Keep the alpha invite-only and use synthetic or explicitly consented documents.

## Closed beta: weeks 9–10

Recruit 20–30 caregivers. Ask them to use the product after one real care event. Interview at least 10.

Track completion rate, task edits, shares, seven-day and 30-day return, and recurring corrections.

## Decision point: weeks 11–12

Continue when:

- 40%+ of invited testers complete a care event;
- 25%+ return for a second event within 30 days;
- 70%+ of generated tasks require only minor/no editing;
- five or more users request continued access or say they would pay.

Otherwise revise the workflow before adding features.

## Pricing

Free beta → free single-profile tier → $9–15/month or $79–119/year family plan for unlimited events, multiple profiles, family access, exports, and longer storage.

## Post-MVP sequence

1. Improve capture/review and source citations.
2. Add controlled family access and task assignment.
3. Deepen one validated workflow: discharge, medication-change, caregiver handoff, or insurance documents.
4. Consider provider integrations and HIPAA-capable operations only after validated consumer traction or a signed partner demand.

## Child-care expansion track

After validating the current elder-care MVP, test a separate child-care experience for parent-entered feeding and sleep routines plus an age-based development roadmap. Keep this track gated from the core MVP so it does not dilute the first-user promise or introduce unreviewed pediatric guidance.

The planned sequence is:

1. Validate routine handoff and well-child visit preparation with parents and pediatric reviewers.
2. Prototype flexible feeding windows, sleep windows, quick logs, reminders, caregiver handoff, and a low-light night mode on mobile and web.
3. Build a private routine alpha with local-first logging, authenticated sync, timezone/DST handling, System/Light/Dark appearance settings, export, and deletion.
4. Add a sourced, versioned CDC milestone roadmap with caregiver observations and visit questions—never a score or screening result.
5. Run a closed family beta after pediatric safety, accessibility, security, privacy, and child-data legal reviews.

See [the child-care expansion roadmap](CHILD_CARE_ROADMAP.md) for scope, data model, experience architecture, release gates, and metrics.

## Business readiness before paid public launch

- Consider a single-member LLC, EIN, separate business bank account, and insurance.
- Complete state/city business and tax checks.
- Publish terms and privacy policy.
- Get counsel to review consumer-health data handling and claims.
- Use Google Play Billing for in-app digital subscriptions, unless an applicable exception applies.

## Key metrics

| Metric | Early target |
| --- | ---: |
| Capture → action plan completion | 60%+ |
| Time to first useful plan | Under 5 minutes |
| Tasks accepted with minor/no edits | 70%+ |
| Family update shared | 30%+ |
| 30-day return | 25%+ |
| Users willing to pay | 20%+ |
