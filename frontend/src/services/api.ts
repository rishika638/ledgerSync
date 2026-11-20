import {
  MetricSummary,
  ImportBatch,
  CsvImportResult,
  FinanceRecord,
  ReconciliationRun,
  ReconciliationMatch,
  Discrepancy,
  InvestigationWorkspacePayload,
  AiInvestigation,
  ResolutionAction,
  AuditEvent,
  AssistantResponse,
  FinanceSourceType,
  DiscrepancyStatus,
  DiscrepancySeverity,
  DiscrepancyType,
  MatchType,
  ResolutionActionType,
  AuthSession
} from '../types';

const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api/v1';
const MUTATING_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

async function request<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const defaultHeaders: Record<string, string> = {
    'Accept': 'application/json',
  };
  const method = (options.method || 'GET').toUpperCase();

  if (!(options.body instanceof FormData)) {
    defaultHeaders['Content-Type'] = 'application/json';
  }
  if (MUTATING_METHODS.has(method)) {
    const csrfResponse = await fetch(`${API_BASE}/auth/csrf`, {
      credentials: 'include',
      headers: { 'Accept': 'application/json' },
    });
    if (!csrfResponse.ok) {
      throw new Error(`Unable to obtain request security token (HTTP ${csrfResponse.status})`);
    }
    const csrfPayload = await csrfResponse.json() as { token?: string };
    if (!csrfPayload.token) {
      throw new Error('The server did not provide a request security token');
    }
    defaultHeaders['X-XSRF-TOKEN'] = csrfPayload.token;
  }

  const response = await fetch(`${API_BASE}${endpoint}`, {
    ...options,
    credentials: 'include',
    headers: {
      ...defaultHeaders,
      ...options.headers,
    },
  });

  if (!response.ok) {
    let errorMsg = `HTTP ${response.status}: ${response.statusText}`;
    try {
      const errBody = await response.json();
      if (errBody.message) errorMsg = errBody.message;
    } catch (_) {}
    throw new Error(errorMsg);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json();
}

export const api = {
  getSession: () => request<AuthSession>('/auth/session'),

  login: (username: string, password: string) =>
    request<{ status: string; username: string; role: 'OPERATOR' | 'ADMIN' }>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    }),

  logout: () => request<void>('/auth/logout', { method: 'POST' }),

  // Dashboard
  getDashboardMetrics: () => request<MetricSummary>('/finance/dashboard/metrics'),

  // Imports
  uploadCsv: (file: File, sourceType: FinanceSourceType, batchName?: string) => {
    const data = new FormData();
    data.append('file', file);
    data.append('sourceType', sourceType);
    if (batchName) data.append('batchName', batchName);
    return request<CsvImportResult>('/finance/imports/upload', {
      method: 'POST',
      body: data,
    });
  },

  listBatches: (page = 0, size = 20) =>
    request<{ content: ImportBatch[]; totalElements: number; totalPages: number }>(
      `/finance/imports/batches?page=${page}&size=${size}`
    ),

  getBatchRecords: (batchId: string, page = 0, size = 20) =>
    request<{ content: FinanceRecord[]; totalElements: number; totalPages: number }>(
      `/finance/imports/batches/${batchId}/records?page=${page}&size=${size}`
    ),

  loadDemoDataset: (forceReload = false) =>
    request<{ alreadyLoaded: boolean; message: string; ledgerRecordsLoaded: number; razorpayRecordsLoaded: number; bankRecordsLoaded: number }>(
      `/finance/imports/demo/load?forceReload=${forceReload}`,
      { method: 'POST' }
    ),

  resetDemo: () =>
    request<{ message: string }>('/finance/imports/demo/reset', { method: 'POST' }),

  downloadTemplate: async (source: string) => {
    const res = await fetch(`${API_BASE}/finance/imports/templates/${source}`, { credentials: 'include' });
    if (!res.ok) {
      throw new Error(`Unable to download template (HTTP ${res.status})`);
    }
    const blob = await res.blob();
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${source}_template.csv`;
    a.click();
  },

  // Reconciliation Runs
  startReconciliation: () =>
    request<ReconciliationRun>('/finance/reconciliation/run', { method: 'POST' }),

  listRuns: (page = 0, size = 20) =>
    request<{ content: ReconciliationRun[]; totalElements: number; totalPages: number }>(
      `/finance/reconciliation/runs?page=${page}&size=${size}`
    ),

  getRun: (id: string) =>
    request<ReconciliationRun>(`/finance/reconciliation/runs/${id}`),

  getRunMatches: (id: string, page = 0, size = 25, matchType?: MatchType, query?: string) => {
    const params = new URLSearchParams({ page: page.toString(), size: size.toString() });
    if (matchType) params.append('matchType', matchType);
    if (query) params.append('query', query);
    return request<{ content: ReconciliationMatch[]; totalElements: number; totalPages: number }>(
      `/finance/reconciliation/runs/${id}/matches?${params.toString()}`
    );
  },

  exportRunMatchesUrl: (id: string) => `${API_BASE}/finance/reconciliation/runs/${id}/export`,

  // Transactions
  searchTransactions: (page = 0, size = 25, query?: string, sourceType?: FinanceSourceType, status?: string) => {
    const params = new URLSearchParams({ page: page.toString(), size: size.toString() });
    if (query) params.append('query', query);
    if (sourceType) params.append('sourceType', sourceType);
    if (status) params.append('status', status);
    return request<{ content: FinanceRecord[]; totalElements: number; totalPages: number }>(
      `/finance/transactions?${params.toString()}`
    );
  },

  getTransactionTimeline: (id: string) =>
    request<{
      primaryRecord: FinanceRecord;
      relatedRecords: FinanceRecord[];
      discrepancies: Discrepancy[];
      auditLogs: AuditEvent[];
    }>(`/finance/transactions/${id}/timeline`),

  // Exceptions & AI Investigation Workspace
  searchExceptions: (page = 0, size = 20, status?: DiscrepancyStatus, severity?: DiscrepancySeverity, type?: DiscrepancyType, runId?: string, query?: string) => {
    const params = new URLSearchParams({ page: page.toString(), size: size.toString() });
    if (status) params.append('status', status);
    if (severity) params.append('severity', severity);
    if (type) params.append('type', type);
    if (runId) params.append('runId', runId);
    if (query) params.append('query', query);
    return request<{ content: Discrepancy[]; totalElements: number; totalPages: number }>(
      `/finance/exceptions?${params.toString()}`
    );
  },

  getWorkspaceData: (id: string) =>
    request<InvestigationWorkspacePayload>(`/finance/exceptions/${id}`),

  triggerInvestigation: (id: string) =>
    request<AiInvestigation>(`/finance/exceptions/${id}/investigate`, { method: 'POST' }),

  executeAction: (id: string, actionType: ResolutionActionType, notes?: string, financialAdjustmentMinor?: number) =>
    request<ResolutionAction>(`/finance/exceptions/${id}/actions`, {
      method: 'POST',
      body: JSON.stringify({ actionType, notes, financialAdjustmentMinor }),
    }),

  // Finance Assistant
  askAssistant: (query: string) =>
    request<AssistantResponse>('/finance/assistant/query', {
      method: 'POST',
      body: JSON.stringify({ query }),
    }),

  // Audit Trail
  getAuditTrail: (page = 0, size = 25, resourceType?: string) => {
    const params = new URLSearchParams({ page: page.toString(), size: size.toString() });
    if (resourceType) params.append('resourceType', resourceType);
    return request<{ content: AuditEvent[]; totalElements: number; totalPages: number }>(
      `/finance/audit?${params.toString()}`
    );
  },

  // Settings
  getSettings: () => request<any>('/finance/settings'),
};
