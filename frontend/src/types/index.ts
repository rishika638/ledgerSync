export type FinanceSourceType = 'LEDGER' | 'RAZORPAY' | 'BANK';

export type DiscrepancySeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export type DiscrepancyStatus = 'OPEN' | 'INVESTIGATING' | 'INVESTIGATED' | 'AWAITING_APPROVAL' | 'RESOLVED' | 'IGNORED';

export type DiscrepancyType =
  | 'MISSING_GATEWAY'
  | 'MISSING_BANK'
  | 'AMOUNT_MISMATCH'
  | 'DUPLICATE_RECORD'
  | 'FEE_MISMATCH'
  | 'TAX_MISMATCH'
  | 'PARTIAL_SETTLEMENT'
  | 'DELAYED_SETTLEMENT'
  | 'REFERENCE_MISMATCH'
  | 'AMBIGUOUS_MATCH'
  | 'MISSING_IN_LOCAL'
  | 'MISSING_IN_REMOTE'
  | 'STATUS_MISMATCH';

export type MatchType =
  | 'EXACT_THREE_WAY'
  | 'TWO_WAY_LEDGER_GATEWAY'
  | 'TWO_WAY_GATEWAY_BANK'
  | 'PARTIAL_SETTLEMENT'
  | 'COMPOSITE';

export type ResolutionActionType =
  | 'APPROVE_ADJUSTMENT'
  | 'REJECT_INVESTIGATION'
  | 'REQUEST_EVIDENCE'
  | 'FORCE_MATCH'
  | 'WRITE_OFF'
  | 'INITIATE_CHARGEBACK_DISPUTE'
  | 'RETRY_SETTLEMENT';

export interface AuthSession {
  authenticated: boolean;
  username?: string;
  role?: 'OPERATOR' | 'ADMIN';
}

export interface MetricSummary {
  totalTransactions: number;
  matchedRecords: number;
  unmatchedRecords: number;
  totalExceptions?: number;
  pendingSettlements: number;
  unmatchedExposureMinor: number;
  unmatchedExposureInr: string;
  matchRate: number;
  precisionRate: number | null;
  recallRate: number | null;
  latestRunId: string | null;
  lastRunTimestamp: string | null;
  durationMs: number;
  exceptionDistributionByType: Record<string, number>;
  exceptionDistributionBySeverity: Record<string, number>;
  reconciliationTrends: Array<{
    id: string;
    date: string;
    matchRate: number;
    matchedCount: number;
    discrepanciesCount: number;
    durationMs: number;
  }>;
  recentActivity: AuditEvent[];
  geminiStatus: {
    configured: boolean;
    model: string;
  };
}

export interface ImportBatch {
  id: string;
  batchName: string;
  sourceType: FinanceSourceType;
  filename: string;
  totalRows: number;
  validRows: number;
  invalidRows: number;
  status: 'PROCESSING' | 'COMPLETED' | 'FAILED';
  errorSummary: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CsvImportResult {
  batchId: string;
  batchName: string;
  sourceType: FinanceSourceType;
  status: 'PROCESSING' | 'COMPLETED' | 'FAILED';
  totalRows: number;
  validRows: number;
  invalidRows: number;
  duplicateUpload: boolean;
  message: string | null;
  invalidRowErrors: string[];
}

export interface FinanceRecord {
  id: string;
  sourceType: FinanceSourceType;
  recordReference: string;
  orderReference: string | null;
  secondaryReference: string | null;
  currency: string;
  amountMinor: number;
  feeMinor: number;
  taxMinor: number;
  netAmountMinor: number;
  transactionTimestamp: string;
  settlementTimestamp: string | null;
  status: 'UNRECONCILED' | 'MATCHED' | 'PARTIALLY_MATCHED' | 'DISCREPANCY';
  rawMetadata: string | null;
  createdAt: string;
}

export interface ReconciliationRun {
  id: string;
  provider: string;
  status: 'STARTED' | 'COMPLETED' | 'FAILED';
  runType: string;
  totalLedgerRecords: number;
  totalGatewayRecords: number;
  totalBankRecords: number;
  matchedRecordsCount: number;
  unmatchedRecordsCount: number;
  discrepanciesCount: number;
  precisionRate: number | null;
  recallRate: number | null;
  matchRate: number;
  unmatchedAmountMinor: number;
  processingDurationMs: number;
  startedAt: string;
  completedAt: string | null;
  errorMessage: string | null;
}

export interface ReconciliationMatch {
  id: string;
  runId: string;
  matchType: MatchType;
  matchStatus: string;
  ledgerRecord?: FinanceRecord;
  gatewayRecord?: FinanceRecord;
  bankRecord?: FinanceRecord;
  ledgerAmountMinor: number;
  gatewayAmountMinor: number;
  bankAmountMinor: number;
  feeMinor: number;
  taxMinor: number;
  confidenceScore: number;
  matchNotes: string | null;
  createdAt: string;
}

export interface Discrepancy {
  id: string;
  paymentId: string | null;
  providerPaymentId: string | null;
  provider: string;
  type: DiscrepancyType;
  severity: DiscrepancySeverity;
  evidence: string;
  status: DiscrepancyStatus;
  amountMinor: number;
  expectedAmountMinor: number;
  actualAmountMinor: number;
  currency: string;
  ledgerRecordId: string | null;
  gatewayRecordId: string | null;
  bankRecordId: string | null;
  resolutionNotes: string | null;
  resolvedBy: string | null;
  resolvedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AiInvestigation {
  id: string;
  modelVersion: string;
  promptVersion: string;
  classification: string;
  confidenceScore: number;
  summary: string;
  possibleCauses: string; // JSON array string
  supportingEvidence: string; // JSON array string
  recommendedAction: ResolutionActionType;
  actionReasoning: string;
  missingEvidence: string | null;
  requiresHumanApproval: boolean;
  validationPassed: boolean;
  rawResponse: string | null;
  createdAt: string;
}

export interface ResolutionAction {
  id: string;
  actionType: ResolutionActionType;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'EXECUTED' | 'FAILED';
  actor: string;
  decisionNotes: string | null;
  financialAdjustmentMinor: number;
  appliedAt: string;
  createdAt: string;
}

export interface AuditEvent {
  id: string;
  action: string;
  actor: string;
  resourceType: string;
  resourceId: string;
  correlationId: string | null;
  beforeState: string | null;
  afterState: string | null;
  createdAt: string;
}

export interface InvestigationWorkspacePayload {
  discrepancy: Discrepancy;
  ledgerRecord: FinanceRecord | null;
  gatewayRecord: FinanceRecord | null;
  bankRecord: FinanceRecord | null;
  investigation: AiInvestigation | null;
  actions: ResolutionAction[];
  auditTrail: AuditEvent[];
}

export interface AssistantResponse {
  answer: string;
  evidenceCitations: string[];
  suggestedQuestions: string[];
  groundTruthMetrics: Record<string, any>;
}
