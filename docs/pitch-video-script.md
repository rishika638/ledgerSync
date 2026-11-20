# LedgerSync 2.0 — Official 5-Minute Pitch & Product Demo Video Script
**Target:** Razorpay Buildathon — Track 4: AI Finance Controller  
**Format:** Natural, Technically Rigorous, Synchronized Screencast & Narration  
**Duration:** Exactly 5:00 (300 seconds)

---

## Technical Pre-Flight & Recording Checklist

Before hitting record:
- [ ] Backend running on `http://localhost:8080` (Spring Boot 3.3.4, Java 21, PostgreSQL 16).
- [ ] Frontend running on `http://localhost:5173` (Vite, dark navy theme `#0B0F19`, clean zoom 100%).
- [ ] Optional: `GEMINI_API_KEY` in `.env`; otherwise demonstrate the deterministic fallback in segment 4.
- [ ] Clean browser window at 1920x1080 resolution, full screen, address bar clean.
- [ ] Mouse cursor set to smooth highlight pointer.

---

## Segment-by-Segment Synchronized Script

### Segment 1: The Problem — The Silent Cash Leak in Financial Settlement
**Timestamp:** `0:00 – 0:35` (35 seconds)  
**Exact Screen:** Slide / Problem Visual / High-level architecture overview  
**Visual Outcome:** Title card transitioning to financial workflow discrepancy diagram: Merchant Ledger vs. Razorpay Settlements vs. Bank Statement.

**Spoken Script:**
> *"Every month, growing digital businesses process thousands of transactions across payment gateways like Razorpay. But behind the scenes, finance teams fight a quiet nightmare: reconciliation.*
> 
> *For example, a customer may pay ₹1,500 in a merchant database. Razorpay settles ₹1,464.60 after deducting MDR fees and GST, and the bank receives a bulk NEFT credit with an unclear narration string.*
> 
> *When numbers don't match, spreadsheets break down. Dropped webhooks, fee miscalculations, delayed nodal settlements, and duplicate charges create undetected financial exposure that takes days of manual investigation to trace.*
> 
> *Welcome to LedgerSync 2.0: an enterprise AI Finance Controller that replaces manual reconciliation with deterministic three-way matching and evidence-grounded Gemini AI investigation."*

---

### Segment 2: Introduction & The Financial Controller Dashboard
**Timestamp:** `0:35 – 1:05` (30 seconds)  
**Exact Screen:** `/dashboard`  
**Exact Buttons & Actions:** Cursor hovers over KPI cards, moves down to recent activity table.  
**Visual Outcome:** Dashboard with current-run metrics and recent audit activity. Read the displayed values; do not imply an external Gemini connection unless one is configured and verified.

**Spoken Script:**
> *"Here is the LedgerSync 2.0 Controller Dashboard. Designed specifically for fintech operators, it maintains a strict invariant: all monetary values are tracked in integer minor currency units—cents and paise—completely eliminating floating-point rounding errors.*
> 
> *The dashboard shows current three-way reconciliation activity and finance metrics. AI availability depends on server-side configuration; without a Gemini key, investigations use the labeled deterministic fallback.*
> 
> *Let's import the demo financial records and watch the platform in action."*

---

### Segment 3: Ingestion, 3-Way Reconciliation & Deterministic Accuracy
**Timestamp:** `1:05 – 2:00` (55 seconds)  
**Exact Screen:** `/imports` transitioning to `/reconciliation`  
**Exact Buttons & Actions:**
1. Navigate to `/imports`.
2. Click **"Load Demo Dataset (165 records)"**.
3. Point out the 3 batches loaded: Merchant Ledger (`ledger.csv`), Razorpay Settlements (`razorpay_settlements.csv`), and Bank Statement (`bank_statement.csv`).
4. Click top navbar button **"Run Reconciliation"**.
5. Wait for the run result and use the duration shown by the application; do not substitute a fixed latency.
6. Navigate to `/reconciliation` and click **"Details"** on the latest run.  
**Visual Outcome:** Show the actual current run and its measured duration. Do not quote the historical 165-case result as a current-code result; the existing run predates batch snapshots. A fresh evaluator result is available only after applying V6 to a dedicated database and running a new reconciliation.

**Spoken Script:**
> *"Let's navigate to Data Sources. LedgerSync accepts CSV feeds from three distinct entities: internal merchant orders, Razorpay settlement reports with MDR and GST breakdowns, and HDFC bank statements.*
> 
> *I'll click 'Load Demo Dataset'. Under the hood, this loads a reproducible ground-truth dataset of 165 logical payments. Each record is parsed, validated, and persisted into PostgreSQL.*
> 
> *Now, let's trigger the engine: I click 'Run Reconciliation'.*
> 
> *The deterministic engine checks the imported records and persists matches and discrepancies. The displayed run duration depends on the local environment.*
> 
> *The displayed match and exception counts come from this run. The included labels are synthetic, in-sample test data, not an independent held-out benchmark. We do not claim production accuracy or generalization from them.*

---

### Segment 4: The Investigation Workspace
**Timestamp:** `2:00 – 3:10` (70 seconds)  
**Exact Screen:** `/exceptions` -> Click into specific exception `/exceptions/dsc_...` (e.g., `MISSING_BANK` or `AMOUNT_MISMATCH`)  
**Exact Buttons & Actions:**
1. On `/exceptions`, show filters for `CRITICAL`, `AWAITING_APPROVAL`, `RESOLVED`.
2. Select a current exception and read its displayed severity and amount.
3. Land on `/exceptions/:id` (The Flagship 3-Column Screen).
4. Review the linked records and amounts actually shown in the evidence panel.
5. Trigger an investigation.
6. Confirm the provider reported by the response. A configured and reachable Gemini API is required to demonstrate a live Gemini result; otherwise show the labeled fallback.
**Visual Outcome:** The workspace shows the saved investigation response and recommendation for the selected exception. Do not narrate a fixed confidence, model, or recommendation unless it appears in the verified response.

**Spoken Script:**
> *"Now let's inspect the flagship capability: The Investigation Workspace.*
> 
> *I open a current exception and inspect its severity and evidence. The workspace links the relevant source records; the amounts and references shown here come from the selected demo records.*
> 
> *I click Investigate. If Gemini is configured and reachable, the backend can request an investigation from it; otherwise the application reports and uses its deterministic fallback.*
> 
> *The backend builds a constrained context from the selected records and returns a structured investigation response. The provider and evidence are visible in the saved result.*
> 
> *Review the diagnosis, cited records, and suggested action against the actual evidence before deciding whether to proceed.*
> 
> *"The backend validates the saved investigation response against domain rules and records. The AI has no approval authority or direct write access."*

---

### Segment 5: Human-in-the-Loop Approval & Audit History
**Timestamp:** `3:10 – 4:00` (50 seconds)  
**Exact Screen:** `/exceptions/:id` Right Column -> Confirmation Modal -> `/audit`  
**Exact Buttons & Actions:**
1. Click **"Approve Recommended Action"** in Right Column.
2. Confirmation Modal appears with adjustment amount and rationale field.
3. Type note: *"Authorized dispute ticket with Razorpay Nodal Operations; UTR verified."*
4. Click **"Confirm & Execute Action"**.
5. Discrepancy status updates instantly to `RESOLVED` with green badge.
6. Scroll down to show updated Resolution History and linked Audit Trail.
7. Click **"Full Audit Trail"** to navigate to `/audit`.  
**Visual Outcome:** The action and discrepancy status are updated, and a linked audit event records the actor and state change.

**Spoken Script:**
> *"Now we enforce human-in-the-loop control. On the right panel, the platform displays the recommended action.*
> 
> *I click 'Approve Recommended Action'. A confirmation modal opens. I review the financial adjustment and enter my controller rationale: 'Authorized dispute ticket with Razorpay Nodal Operations; UTR verified.'*
> 
> *I confirm. The backend records the resolution action, updates the discrepancy status, and adds a linked audit event.*
> 
> *Switching to the Audit Trail screen, I can inspect the recorded actor, timestamp, resource, and state change for this action. This application audit history is not a substitute for an independently validated compliance or tamper-proof archive.*

---

### Segment 6: Local Reliability & Deterministic AI Fallback
**Timestamp:** `4:00 – 4:35` (35 seconds)  
**Exact Screen:** `/settings` and `/assistant`  
**Exact Buttons & Actions:**
1. Navigate to `/assistant`.
2. Enter **"What is our current unmatched financial exposure?"**
3. Review the answer against the active dashboard metrics.
4. Navigate to `/settings`.
5. Point out the configured AI availability state without exposing secret values.  
**Visual Outcome:** The assistant displays its response and Settings shows current configuration; do not describe an unverified external Gemini connection.

**Spoken Script:**
> *"Financial software must never fail silently. In `/assistant`, controllers can ask questions in natural language. When I ask: 'What is our total unmatched financial exposure?', the assistant executes read-only Spring Data repository queries first, fetching grounded facts before synthesizing the answer. Zero model-generated SQL is ever executed.*
> 
> *When Gemini is unavailable or no key is configured, the backend can use its deterministic rule-based fallback. Verify the provider label in the response rather than assuming an external AI request succeeded.*

---

### Segment 7: Conclusion & Deployment Readiness
**Timestamp:** `4:35 – 5:00` (25 seconds)  
**Exact Screen:** `/dashboard` or a summary slide with the repository; do not display cloud URLs unless deployment is completed and verified.
**Visual Outcome:** Smooth pan across dashboard KPIs, architecture diagram badge, and Razorpay Buildathon summary.

**Spoken Script:**
> *"To summarize: LedgerSync 2.0 transforms financial reconciliation from a fragile manual chore into a deterministic, AI-assisted operational engine.*
> 
> *Built on Java 21, Spring Boot 3.3.4, PostgreSQL, and React, LedgerSync can be prepared for AWS and Vercel after the infrastructure, credentials, domains, and production security configuration are completed and verified. No cloud deployment is claimed here.*
> 
> *Deterministic matching. Evidence-based AI investigation. Safe human-controlled resolution. That is LedgerSync 2.0. Thank you."*

---

## Shot & Scene Visual Checklist

| Scene # | Timestamp | Camera / Focus | Screen Element | Audio Cue |
|---|---|---|---|---|
| 1 | 0:00 - 0:35 | Full Screen Slide | 3-way reconciliation architecture diagram | *"Every month, growing digital businesses..."* |
| 2 | 0:35 - 1:05 | Browser Screencast | `/dashboard` overview & metric cards | *"Here is the LedgerSync 2.0 Controller Dashboard..."* |
| 3 | 1:05 - 2:00 | Browser Screencast | `/imports` -> Load Demo -> `/reconciliation` | *"Let's navigate to Data Sources..."* |
| 4 | 2:00 - 3:10 | Browser Screencast | A current exception (3-column layout) | *"Now let's inspect the flagship capability..."* |
| 5 | 3:10 - 4:00 | Browser Screencast | Confirmation Modal -> Approve -> `/audit` | *"Now we enforce human-in-the-loop control..."* |
| 6 | 4:00 - 4:35 | Browser Screencast | `/assistant` Q&A & `/settings` | *"Financial software must never fail silently..."* |
| 7 | 4:35 - 5:00 | Browser Screencast | `/dashboard` summary & closing credentials | *"To summarize: LedgerSync 2.0 transforms..."* |
