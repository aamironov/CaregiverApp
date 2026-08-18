# CareBinder child-care expansion roadmap

**Status:** Post-MVP product plan  
**Last updated:** August 12, 2026

## 1. Product intent

Extend CareBinder for a parent or authorized caregiver coordinating a child's daily routines and development. The expansion should help caregivers:

- plan and hand off feeding and sleep routines;
- record what actually happened without creating a compliance score;
- notice and remember developmental observations; and
- prepare concise questions and summaries for well-child visits.

This is a separate post-MVP track. It does not change the current elder-care MVP or its validation targets.

## 2. Product and safety boundaries

CareBinder is an organizer for caregiver-entered or clinician-provided plans. It must not:

- prescribe feeding amounts, intervals, foods, sleep duration, sleep training, or developmental activities;
- tell a caregiver to delay feeding until a scheduled time or override a child's hunger/fullness cues;
- assess growth, nutrition, sleep quality, developmental age, delay, diagnosis, or prognosis;
- present a missed routine or milestone as an emergency;
- recommend sleep products, positions, or environments; or
- replace pediatric advice, validated developmental screening, or emergency services.

Every routine plan must identify who entered it and optionally record its source, such as “parent plan” or “pediatrician instructions.” Imported educational content must show its source and review date.

## 3. Planned capability: feeding routines

### Parent jobs

- See the next planned feeding window and recent feeding history.
- Record a feeding quickly during a handoff or overnight.
- Share caregiver-entered instructions without relying on memory or chat history.
- Compare the plan with what happened without receiving an adherence grade.

### MVP for this capability

1. Create a recurring feeding plan using a start time or flexible window.
2. Choose a caregiver-defined label, such as breastfeed, bottle, meal, or snack.
3. Add optional caregiver-entered instructions, amount and unit, and plan source.
4. Send a neutral reminder that can be logged, snoozed, or skipped.
5. Record actual start time, optional amount and unit, notes, and caregiver.
6. Show planned and logged events distinctly in Today and History.
7. Provide a handoff view with the last feeding, next planned window, and caregiver notes.

### Interaction rules

- Use “planned window” and “not logged” rather than “late,” “missed,” or “noncompliant.”
- A reminder never instructs the caregiver to wait to feed a hungry child.
- Amounts are optional free-entry records; the app does not calculate a recommended amount.
- Food and allergen notes may be recorded later, but the app does not recommend introduction timing or interpret symptoms.
- When caregivers have concerns about how much or how little a child eats, link to professional care rather than generating advice.

## 4. Planned capability: sleep routines

### Parent jobs

- Coordinate nap, bedtime, and wake routines across caregivers.
- Record sleep start, wake time, and interruptions with minimal interaction.
- See recent patterns without a health or quality score.

### MVP for this capability

1. Create recurring nap and bedtime windows.
2. Add an optional caregiver-authored routine checklist.
3. Remind the active caregiver at the start of a window.
4. Log sleep start, wake time, interruptions, location label, and notes.
5. Offer an overnight low-light entry mode and one-tap “start sleep” / “awake” actions.
6. Show the last sleep event and next planned window in the caregiver handoff.
7. Link to current NICHD Safe to Sleep guidance from sleep setup and help surfaces.

### Interaction rules

- Do not score “good” or “bad” sleep or prescribe a sleep-training method.
- Do not infer safety from a location label or logged duration.
- Safe-sleep information must remain source-attributed and versioned; CareBinder should not silently convert it into personalized advice.
- Reminder copy stays neutral and never claims that an expected sleep window is medically necessary.

## 5. Planned capability: night mode

Night mode is a low-light presentation of the same product, data, actions, and safety content. It is not a separate account, workflow, or simplified safety mode.

### Parent jobs

- Log a feeding, sleep start, wake-up, or note without lighting the room brightly.
- Read the next routine and confirm the active child with minimal visual effort.
- Keep display preferences consistent across mobile and web where practical.

### MVP for this capability

1. Support **System**, **Light**, and **Dark** appearance settings, with **System** as the default.
2. Add an optional scheduled night preference using caregiver-defined start and end times.
3. Apply the night theme to every screen, dialog, loading state, error state, chart, and notification-related preview owned by the app.
4. Provide a focused overnight quick-log surface for feeding, sleep start, wake-up, and short notes.
5. Preserve the selected child, timestamps, units, confirmation steps, and undo behavior from daytime mode.
6. Synchronize the preference to the caregiver account while allowing a device-specific override.
7. Make theme changes immediate without losing unsaved form data or restarting an active timer.

### Visual and interaction rules

- Use near-black and dark neutral surfaces rather than pure black everywhere; avoid bright white panels and saturated decorative colors.
- Maintain WCAG 2.2 AA contrast for text, controls, focus indicators, errors, and status labels in both themes.
- Do not rely on color alone to distinguish planned, logged, skipped, warning, or disabled states.
- Keep touch targets and type sizes unchanged; night mode must not hide secondary safety information or confirmation steps.
- Reduce decorative motion and respect the operating system's reduced-motion setting.
- Never change stored content, reminder urgency, or routine meaning based on theme or time of day.
- Avoid presenting night mode as a way to improve a child's sleep; it is solely a caregiver interface preference.

### Web-specific behavior

- Respect the browser/operating-system color-scheme preference before sign-in.
- After sign-in, apply the caregiver's saved preference unless a device override exists.
- Define dark tokens for surfaces, text, borders, focus rings, semantic states, shadows, and data visualizations rather than inverting colors automatically.
- Set the browser theme color to match the active top-level surface and prevent a bright flash while the saved theme loads.

## 6. Planned capability: child development roadmap

### Parent jobs

- Understand which developmental observations are relevant to the child's age.
- Record concrete examples without labeling the child.
- Remember questions for the next visit and share an accurate summary.

### MVP for this capability

1. Build an age-based timeline from CDC developmental milestone content for ages 2 months through 5 years.
2. Group milestones into social/emotional, language/communication, cognitive, and movement/physical categories.
3. Let a caregiver mark an item **Noticed**, **Not sure**, or leave it unmarked.
4. Allow a dated, plain-text observation, such as a concrete example of what the caregiver saw.
5. Add any observation or concern to a **Questions for the next visit** list.
6. Export a caregiver-reviewed visit summary showing observations, dates, questions, and content sources.
7. Let families add their own non-clinical goals or memories in a separate section that is never mixed with sourced milestones.

### Interaction rules

- Do not show a percentage complete, pass/fail result, developmental age, prediction, or comparison with other children.
- Use the CDC's published age group and wording with source/version metadata; do not have AI invent milestones.
- State that milestone checklists are not substitutes for standardized, validated developmental screening.
- If a caregiver selects **Not sure** or records a concern, offer a neutral action: add it to visit questions and contact the child's healthcare provider. Do not diagnose or triage the observation.
- Keep parent-authored observations visually separate from sourced educational content.

## 7. Experience architecture

### Mobile

- **Today:** next feeding window, next nap/bedtime window, last logged event, and quick-log actions.
- **Routines:** Feeding and Sleep tabs with plans, history, and handoff.
- **Development:** age-based timeline, observations, and visit questions.
- **Profile:** child details, caregivers, timezone, units, appearance, notification settings, exports, and deletion.

### Web UI

- Use the same information architecture in a left navigation rail.
- Optimize the main canvas for weekly routine planning, history review, and visit-summary preparation.
- Preserve the same labels and safety boundaries as mobile; web must not add analytics that imply a health assessment.
- Provide the same System / Light / Dark choices and ensure routine planning remains readable in either theme.

## 8. Proposed domain model

| Entity | Purpose | Key fields |
| --- | --- | --- |
| `ChildProfile` | Identifies the child for authorized caregivers | display name, birth date, timezone, preferred units |
| `RoutinePlan` | Caregiver-entered recurring plan | type, label, recurrence, time/window, instructions, author, source, active dates |
| `RoutineOccurrence` | One planned instance | plan ID, planned window, reminder state, skipped state |
| `RoutineLog` | What actually happened | type, start/end, amount/unit, notes, author, created/edited timestamps |
| `DevelopmentMilestone` | Versioned sourced content | age group, category, wording, source URL, source version/review date |
| `MilestoneObservation` | Caregiver's observation | milestone ID, state, example, observed date, author |
| `VisitQuestion` | Caregiver-reviewed question | text, linked observation, status, created date |
| `AppearancePreference` | Caregiver and device theme behavior | account mode, optional night schedule, device override, updated timestamp |

Planned occurrences and actual logs remain separate records. Editing a recurring plan must not rewrite historical logs.

## 9. Delivery roadmap

### Phase 0 — discovery and safety definition (2–3 weeks)

- Interview 12–15 parents or guardians with recent multi-caregiver handoff experience.
- Interview 3–5 pediatric professionals about routine coordination, misleading terminology, and escalation boundaries.
- Test whether feeding windows, sleep windows, and visit-question preparation solve recurring problems.
- Complete a child-data privacy assessment and clinical-content review plan.

**Gate:** at least eight caregivers independently describe routine handoff or visit-preparation pain, and reviewers approve the product boundaries.

### Phase 1 — schedule prototype (2–3 weeks)

- Prototype one child profile, feeding plan, sleep plan, reminders, quick logs, Today, and handoff.
- Prototype System / Light / Dark themes and the overnight quick-log surface.
- Test flexible windows, overnight entry, theme switching, skipped events, and caregiver attribution.
- Test both mobile and responsive web layouts.

**Gate:** 80% of participants can create a plan and log an event without coaching; night-mode participants can complete an overnight log without switching themes; no participant interprets a reminder as medical urgency or instruction to delay care.

### Phase 2 — private routine alpha (4–6 weeks)

- Implement local-first routine storage and authenticated sync.
- Add timezone and daylight-saving behavior, offline logging, notification controls, export, and deletion.
- Implement semantic day/night design tokens, system-theme response, an optional night schedule, and account/device preference behavior.
- Add audit history for plan edits and caregiver attribution.
- Run accessibility in both themes, notification-copy, privacy, and pediatric safety reviews.

**Gate:** reminder delivery and history pass timezone/DST tests; all supported screens pass theme, contrast, dynamic-type, and no-bright-flash checks; export and deletion are verified; no unresolved high-severity safety or privacy findings.

### Phase 3 — development roadmap alpha (4–5 weeks)

- Import and version CDC milestone content rather than generating it.
- Add Noticed / Not sure observations, visit questions, and reviewed export.
- Validate wording and workflows with parents and pediatric reviewers.

**Gate:** users understand that the roadmap is observational and not a screening result; every milestone is traceable to a reviewed source version.

### Phase 4 — closed family beta (6–8 weeks)

- Recruit 20–30 families with more than one participating caregiver.
- Measure routine setup, logging, handoffs, return use, visit-question creation, and corrections.
- Interview at least 10 families and repeat pediatric, accessibility, security, and privacy reviews.

**Gate:** continued use demonstrates coordination value without evidence that families are treating the app as medical advice or developmental screening.

### Later, only after validation

- Multiple children and reusable caregiver schedules.
- Caregiver roles and controlled invitations.
- Pediatrician-provided custom plans with explicit provenance.
- Calendar or provider integrations after security, consent, and partner validation.

## 10. Release acceptance criteria

- Only an authorized caregiver can create or change a routine plan.
- The UI always distinguishes planned windows from actual logs.
- Reminders can be disabled, snoozed, or skipped and never imply diagnosis or emergency.
- Recurrence works correctly across timezones and daylight-saving changes.
- Offline entries reconcile without silently overwriting another caregiver's entry.
- Every sourced milestone and safety link records source, review date, and content version.
- Caregivers can export and delete child data; deletion behavior is tested end to end.
- Sensitive child data is excluded from logs, analytics payloads, and notification previews by default.
- Every supported screen and state renders correctly in System, Light, and Dark modes on mobile and web.
- Theme changes preserve unsaved input, active timers, selected child, and navigation position.
- Night mode passes WCAG 2.2 AA contrast checks and never uses color as the only status signal.
- Initial launch and theme restoration do not expose a bright intermediate screen.
- Pediatric safety, accessibility, security, and privacy reviews have no unresolved high-severity findings.
- Counsel assesses whether COPPA, state children's privacy laws, consumer-health privacy rules, or other requirements apply before public launch.

## 11. Success metrics

| Metric | Early target |
| --- | ---: |
| Plan creation completion | 70%+ |
| Routine reminder → log or explicit skip | 60%+ |
| Families using handoff weekly | 40%+ |
| Development users creating a visit question | 25%+ |
| Routine logs requiring correction | Under 10% |
| Overnight logs completed without theme switching | 90%+ |
| 30-day family return | 30%+ |

Do not create adherence, sleep-quality, nutrition, or development scores. Measure whether the product improves coordination, record accuracy, and visit preparation.

## 12. Reference sources for content and product review

- [CDC Infant and Toddler Nutrition](https://www.cdc.gov/infant-toddler-nutrition/index.html)
- [CDC Signs Your Child Is Hungry or Full](https://www.cdc.gov/infant-toddler-nutrition/mealtime/signs-your-child-is-hungry-or-full.html)
- [CDC Developmental Milestones](https://www.cdc.gov/act-early/milestones/index.html)
- [NICHD Safe Sleep Environment](https://safetosleep.nichd.nih.gov/reduce-risk/safe-sleep-environment)
- [FTC Children's Privacy guidance](https://www.ftc.gov/business-guidance/privacy-security/childrens-privacy)
- [W3C Web Content Accessibility Guidelines (WCAG) 2.2](https://www.w3.org/TR/WCAG22/)

These sources guide content governance and safety review. They are not a substitute for pediatric, privacy, or legal review of the finished product.
