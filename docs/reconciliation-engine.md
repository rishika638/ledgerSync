# LedgerSync 2.0 — Deterministic Reconciliation Engine

## 1. Overview
LedgerSync 2.0 implements deterministic three-way financial reconciliation across three primary sources:
1. **Merchant Payment Ledger:** Captured order gross records from the application database.
2. **Payment Gateway Settlements (Razorpay):** Net payout reports, merchant discount rate (MDR) fees, and GST tax.
3. **Corporate Bank Statements (e.g. HDFC Bank CMS):** Value dates, credited settlement amounts, and UTR reference narrations.

## 2. Invariants and Monetary Storage
- **Integer Minor Units:** All financial values (amounts, fees, taxes, adjustments) are strictly stored and computed in integer minor currency units (cents or paise, `long amount_minor`). Floating-point arithmetic (`float`, `double`) is strictly forbidden for calculations to avoid rounding leakage.
- **Fundamental Settlement Invariant:**
  $$\text{Net Settlement} = \text{Gross Amount} - \text{MDR Fee} - \text{Tax} + \text{Signed Adjustments}$$
- **Contractual MDR Fee Schedule:**
  $$\text{Fee} = \text{round}(\text{Gross} \times 0.02), \quad \text{Tax} = \text{round}(\text{Fee} \times 0.18)$$

## 3. Matching Phases

### Phase 1: Reference and Order Matching
- Primary index: `orderReference` (e.g., `ORD-1001`).
- Secondary index: `recordReference` (`payment_id`) and `secondaryReference` (ARN / UTR).
- Matches where Ledger Gross equals Gateway Gross and Bank Credit equals Gateway Net within settlement window SLA are confirmed as `EXACT_THREE_WAY`.

### Phase 2: Discrepancy Detection & Classification
When records deviate from invariants, one of 10 typed exceptions is generated:
1. `MISSING_GATEWAY`: Captured in merchant ledger, but absent from Razorpay report.
2. `MISSING_BANK`: Confirmed settled on Razorpay, but zero matching bank statement credit.
3. `AMOUNT_MISMATCH`: Gross amount in merchant cart differs from gross charged by Razorpay.
4. `DUPLICATE_RECORD`: Multiple identical payment IDs or repeated settlement lines for the same order.
5. `FEE_MISMATCH`: MDR fee does not equal the configured 2.0% gross fee calculation.
6. `TAX_MISMATCH`: GST tax on MDR fee miscalculated.
7. `PARTIAL_SETTLEMENT`: Gateway settled only a partial tranche (e.g., 60% gross), leaving an unsettled balance.
8. `DELAYED_SETTLEMENT`: Settlement completed after the configured 72-hour threshold. Confirm this implementation threshold against the merchant's contract before production use.
9. `REFERENCE_MISMATCH`: Net amount matches bank credit, but bank narration omits order reference.
10. `AMBIGUOUS_MATCH`: Multiple transactions share identical amount and timestamp with generic narration. Engine refuses to force match.

### Phase 3: Ground-truth verification

The local evaluator is `.\scripts\evaluate-ground-truth.ps1`. It compares the latest run's records with all 165 labeled synthetic cases and fails if a clean match or expected discrepancy classification is missing.

The latest measured local run classified 165/165 cases correctly: 110 clean cases and 55 discrepancy cases. It created 116 match rows because the six delayed settlements are amount-matched and separately flagged with `DELAYED_SETTLEMENT`. The evaluator does not count those as clean matches.

Precision and recall remain unset (`null`) in reconciliation responses because the service does not persist independently calculated benchmark precision/recall. No fixed latency or production performance claim is made; database-backed run duration varies by environment.
