# Consumer beta privacy and safety boundary

## What CareBinder does

- Organizes family-provided documents and notes.
- Creates editable drafts of summaries, tasks, and questions.
- Lets the caregiver choose what to save, remind, or share.

## What it does not do

- Diagnose, triage symptoms, or recommend care.
- Verify medication dosage, interactions, or safety.
- Replace clinician instructions, emergency services, or a patient portal.
- Send information automatically to a clinician, insurer, or family member.

## Minimum release gates

- Privacy policy and terms of use reviewed by counsel.
- App contains clear in-product disclaimer and user confirmation controls.
- Data deletion function is tested end-to-end.
- Sensitive data is absent from logs, analytics, monitoring, and test fixtures.
- All cloud services and subprocessors are inventoried.
- The privacy notice clearly discloses that selected documents and recordings are sent to Bytez and, for closed-source models, may also pass to the configured model provider.
- The privacy notice also discloses that user-generated text may be sent for on-demand translation; translated responses are not treated as canonical medical instructions.
- Bytez/model-provider retention, training, regional processing, security, and contractual terms are reviewed for the intended data class before any real care information is accepted.
- Support staff procedures prevent asking users to email or paste health documents into support tools.
- Offline snapshots are encrypted, cleared on sign-out/account deletion, and covered by device or browser access controls; release testing includes loss, theft, shared-browser, and cache-clearing scenarios.

## HIPAA boundary

A direct-to-consumer app is not automatically subject to HIPAA. That changes when the company works on behalf of a covered healthcare entity or business associate. Do not claim HIPAA compliance in product or marketing material without a legal review, relevant agreements, and a compliant deployed architecture.

The presence of a Bytez integration does not make this scaffold HIPAA-capable. Use synthetic data until the relevant vendor and provider agreements, technical controls, and legal review are complete.
