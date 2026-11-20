# LedgerSync 2.0 — AI Finance Controller Architecture

## 1. Principles of Financial AI Safety
The AI Controller is designed with five strict non-negotiable architectural boundaries:
1. **Zero SQL Generation / Execution:** Gemini is never granted database write access or permitted to execute arbitrary model-generated SQL.
2. **Read-Only Evidence Feeding:** The backend fetches verified records from PostgreSQL and passes sanitized financial facts into the prompt.
3. **Response Sanitization:** Before persistence, the `InvestigationOutputValidator` replaces model summaries, citations, supporting evidence, possible causes, and missing-source claims with persisted discrepancy/record evidence. It drops uncalibrated confidence and restricts recommendation actions; it does not treat arbitrary model-generated numerical statements as verified.
4. **Mandatory Human-in-the-Loop Sign-off:** Every AI recommendation requires explicit confirmation by an authorized finance operator or admin.
5. **Deterministic Controller Fallback:** If the external Gemini API is unreachable, rate-limited, or disabled, the platform automatically activates a local rule-based diagnostic engine.

## 2. Gemini Pipeline

```
[Discrepancy Event] 
        │
        ▼
[Fetch Ledger, Gateway, Bank Evidence]
        │
        ▼
[PromptBuilder: PII Masking & Context Packaging]
        │
        ▼
[GeminiClient: 10s Timeout, 2 Retries, JSON Mode]
        │
   ┌────┴───────────────────────────┐
   ▼                                ▼
[Gemini API 200 OK]      [API Failure / Rate Limit]
   │                                │
   │                     [Deterministic Fallback]
   ▼                                │
[InvestigationOutputValidator] ◄────┘
   │
   ▼
[Persist to ai_investigations]
   │
   ▼
[Update Discrepancy -> AWAITING_APPROVAL]
   │
   ▼
[Transactional Application-Recorded Audit Event]
```

## 3. Data Handling
- Selected free-text references and metadata have 9–16 digit numeric sequences masked to `XXXXXX9821`; this is not comprehensive PII detection.
- Names, emails, and arbitrary user-provided text are not comprehensively stripped or hashed. Use synthetic data, or confirm authorization and provider data-handling requirements before enabling live Gemini on real financial data.
- Prompt sanitization is not a complete prompt-injection defense. Model output is treated as untrusted and sanitized before it is persisted.

## 4. Output Schema

The payload below is illustrative, not a result from the verified demo run. Its record IDs, confidence, recommendation, causes, and amounts are placeholders and must not be presented as measured or authenticated facts.

```json
{
  "classification": "MISSING_BANK",
  "confidence_score": 96.0,
  "summary": "Razorpay settlement confirmed net payout, but corresponding credit is completely missing from the bank statement.",
  "possible_causes": [
    "Bank transfer failed or was held in nodal clearing account due to NEFT batch cutoff.",
    "Beneficiary account mismatch on statement generation date."
  ],
  "supporting_evidence": [
    "Merchant Ledger captured Order ORD-0119 for ₹1500.00",
    "Razorpay processed Payment pay_Rzp0119 (Gross ₹1500.00, Net ₹1464.60, UTR: UTR98760119)"
  ],
  "referenced_record_ids": ["frec_1a2b3c4d", "frec_5e6f7a8b"],
  "recommended_action": "INITIATE_CHARGEBACK_DISPUTE",
  "action_reasoning": "Settlement funds of ₹1464.60 are overdue. Demand payment confirmation and UTR trace from Razorpay settlement operations.",
  "missing_evidence": "Bank clearing advice from Razorpay nodal desk.",
  "requires_human_approval": true
}
```
