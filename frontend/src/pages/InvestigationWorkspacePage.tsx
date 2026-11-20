import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  ArrowLeft,
  Sparkles,
  CheckCircle2,
  AlertTriangle,
  XCircle,
  FileQuestion,
  ShieldCheck,
  Clock,
  Layers,
  Scale,
  DollarSign,
  TrendingUp,
  Cpu,
  RefreshCw,
  ExternalLink,
  Info
} from 'lucide-react';
import { api } from '../services/api';
import {
  InvestigationWorkspacePayload,
  ResolutionActionType,
  AiInvestigation,
  ResolutionAction,
  AuditEvent
} from '../types';
import { StatusBadge } from '../components/StatusBadge';
import { SeverityBadge } from '../components/SeverityBadge';
import { ConfirmationModal } from '../components/ConfirmationModal';
import { formatMinorUnits } from '../utils/currency';

export const InvestigationWorkspacePage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const [data, setData] = useState<InvestigationWorkspacePayload | null>(null);
  const [loading, setLoading] = useState(true);
  const [investigating, setInvestigating] = useState(false);
  const [actionModal, setActionModal] = useState<{
    isOpen: boolean;
    actionType: ResolutionActionType;
    title: string;
    description: string;
  }>({
    isOpen: false,
    actionType: 'APPROVE_ADJUSTMENT',
    title: '',
    description: '',
  });
  const [modalLoading, setModalLoading] = useState(false);
  const [notification, setNotification] = useState<string | null>(null);

  const loadWorkspace = async () => {
    if (!id) return;
    try {
      setLoading(true);
      const res = await api.getWorkspaceData(id);
      setData(res);
    } catch (err: any) {
      setNotification(`Failed to load exception: ${err.message}`);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadWorkspace();
  }, [id]);

  const handleTriggerInvestigation = async () => {
    if (!id) return;
    try {
      setInvestigating(true);
      const inv = await api.triggerInvestigation(id);
      const source = inv.modelVersion === 'RULE_BASED_FALLBACK' ? 'Rule-based fallback' : 'Gemini';
      setNotification(`${source} investigation completed. Persisted evidence is ready for human review.`);
      await loadWorkspace();
    } catch (err: any) {
      setNotification(`Investigation error: ${err.message}`);
    } finally {
      setInvestigating(false);
    }
  };

  const handleExecuteAction = async (notes: string, adjustmentMinor?: number) => {
    if (!id) return;
    try {
      setModalLoading(true);
      const action = await api.executeAction(id, actionModal.actionType, notes, adjustmentMinor);
      setNotification(`Action ${action.actionType} successfully executed and recorded to audit trail!`);
      setActionModal((prev) => ({ ...prev, isOpen: false }));
      await loadWorkspace();
    } catch (err: any) {
      setNotification(`Action execution failed: ${err.message}`);
    } finally {
      setModalLoading(false);
    }
  };

  if (loading && !data) {
    return (
      <div className="py-24 text-center font-mono text-xs text-slate-400">
        Loading investigation workspace...
      </div>
    );
  }

  if (!data) {
    return (
      <div className="py-24 text-center text-rose-400 font-mono text-sm">
        Exception record not found.
      </div>
    );
  }

  const { discrepancy, ledgerRecord, gatewayRecord, bankRecord, investigation, actions, auditTrail } = data;

  let parsedCauses: string[] = [];
  let parsedEvidence: string[] = [];
  if (investigation?.possibleCauses) {
    try {
      parsedCauses = JSON.parse(investigation.possibleCauses);
    } catch (_) {}
  }
  if (investigation?.supportingEvidence) {
    try {
      parsedEvidence = JSON.parse(investigation.supportingEvidence);
    } catch (_) {}
  }

  const lAmt = ledgerRecord ? formatMinorUnits(ledgerRecord.amountMinor) : null;
  const gGross = gatewayRecord ? formatMinorUnits(gatewayRecord.amountMinor) : null;
  const gNet = gatewayRecord ? formatMinorUnits(gatewayRecord.netAmountMinor) : null;
  const bAmt = bankRecord ? formatMinorUnits(bankRecord.amountMinor) : null;

  return (
    <div className="space-y-6 animate-in fade-in duration-200">
      {/* Top Banner & Breadcrumb */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-[#1E293B] pb-5">
        <div className="flex items-center gap-3">
          <button
            onClick={() => navigate('/exceptions')}
            className="p-2 rounded-lg bg-[#111827] border border-[#1E293B] text-slate-400 hover:text-white transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
          </button>
          <div>
            <div className="flex items-center gap-2">
              <span className="text-xs font-mono uppercase text-slate-500">Flagship Investigation</span>
              <span className="text-slate-600">/</span>
              <h1 className="text-xl font-bold tracking-tight text-slate-100 font-mono">{discrepancy.id}</h1>
              <SeverityBadge severity={discrepancy.severity} />
              <StatusBadge status={discrepancy.status} size="sm" />
            </div>
            <p className="text-xs text-slate-400 mt-1 font-mono">
              Exception Type: <span className="text-teal-300 font-semibold">{discrepancy.type}</span> | Financial Exposure: <span className="text-rose-400 font-bold">₹{formatMinorUnits(discrepancy.amountMinor)}</span>
            </p>
          </div>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={handleTriggerInvestigation}
            disabled={investigating}
            className="flex items-center gap-2 px-4 py-2 bg-teal-600 hover:bg-teal-500 text-white rounded-lg text-xs font-semibold shadow-lg shadow-teal-900/30 transition-all"
          >
            {investigating ? (
              <RefreshCw className="w-4 h-4 animate-spin" />
            ) : (
              <Sparkles className="w-4 h-4" />
            )}
            <span>{investigating ? 'Investigating...' : 'Run AI Investigation'}</span>
          </button>
        </div>
      </div>

      {notification && (
        <div className="bg-[#111827] border border-teal-800 rounded-xl p-3.5 flex items-center justify-between text-xs font-mono text-teal-300">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 text-teal-400 shrink-0" />
            <span>{notification}</span>
          </div>
          <button
            onClick={() => setNotification(null)}
            className="text-slate-400 hover:text-white uppercase font-bold text-[10px]"
          >
            Dismiss
          </button>
        </div>
      )}

      {/* 3-Column Flagship Layout */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* LEFT COLUMN: Source Evidence & 3-Way Financial Comparison (4 cols) */}
        <div className="lg:col-span-4 space-y-4">
          <div className="bg-[#111827] border border-[#1E293B] rounded-xl p-4">
            <h2 className="text-xs font-mono uppercase font-semibold text-slate-300 mb-3 flex items-center gap-2">
              <Scale className="w-4 h-4 text-teal-400" />
              <span>3-Way Financial Comparison</span>
            </h2>

            <div className="space-y-2 text-xs font-mono">
              <div className="flex justify-between p-2 rounded bg-[#0F172A] border border-[#1E293B]">
                <span className="text-slate-400">Merchant Ledger:</span>
                <span className="font-bold text-slate-100">{lAmt ? `₹${lAmt}` : 'MISSING'}</span>
              </div>
              <div className="flex justify-between p-2 rounded bg-[#0F172A] border border-[#1E293B]">
                <span className="text-slate-400">Razorpay Gross:</span>
                <span className="font-bold text-slate-100">{gGross ? `₹${gGross}` : 'MISSING'}</span>
              </div>
              <div className="flex justify-between p-2 rounded bg-[#0F172A] border border-[#1E293B]">
                <span className="text-slate-400">Razorpay Net (Fee/Tax):</span>
                <span className="font-bold text-slate-100">
                  {gNet ? `₹${gNet}` : '—'}
                  {gatewayRecord && (
                    <span className="block text-[10px] text-slate-500 font-normal">
                      Fee: ₹{formatMinorUnits(gatewayRecord.feeMinor)} | GST: ₹{formatMinorUnits(gatewayRecord.taxMinor)}
                    </span>
                  )}
                </span>
              </div>
              <div className="flex justify-between p-2 rounded bg-[#0F172A] border border-[#1E293B]">
                <span className="text-slate-400">Bank Statement Credit:</span>
                <span className="font-bold text-emerald-400">{bAmt ? `₹${bAmt}` : 'MISSING / NO CREDIT'}</span>
              </div>
              <div className="flex justify-between p-2.5 rounded bg-rose-950/30 border border-rose-800 text-rose-300 font-bold">
                <span>Discrepancy Delta:</span>
                <span>₹{formatMinorUnits(discrepancy.amountMinor)}</span>
              </div>
            </div>
          </div>

          {/* 1. Ledger Record Card */}
          <div className="bg-[#111827] border border-[#1E293B] rounded-xl p-4">
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs font-mono font-bold text-blue-400 uppercase">1. Merchant Ledger</span>
              <span className="text-[10px] font-mono text-slate-500">{ledgerRecord?.id || '—'}</span>
            </div>
            {ledgerRecord ? (
              <div className="space-y-1.5 text-xs font-mono text-slate-300">
                <div className="flex justify-between">
                  <span className="text-slate-500">Ref:</span>
                  <span>{ledgerRecord.recordReference}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-500">Order ID:</span>
                  <span className="font-bold text-slate-100">{ledgerRecord.orderReference}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-500">Gross Amount:</span>
                  <span className="text-slate-100 font-bold">₹{formatMinorUnits(ledgerRecord.amountMinor)}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-500">Timestamp:</span>
                  <span className="text-slate-400">{new Date(ledgerRecord.transactionTimestamp).toLocaleString()}</span>
                </div>
              </div>
            ) : (
              <div className="text-xs text-rose-400 font-mono py-2">
                [Record dropped / missing in merchant ledger]
              </div>
            )}
          </div>

          {/* 2. Razorpay Settlement Card */}
          <div className="bg-[#111827] border border-[#1E293B] rounded-xl p-4">
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs font-mono font-bold text-teal-400 uppercase">2. Razorpay Settlement</span>
              <span className="text-[10px] font-mono text-slate-500">{gatewayRecord?.id || '—'}</span>
            </div>
            {gatewayRecord ? (
              <div className="space-y-1.5 text-xs font-mono text-slate-300">
                <div className="flex justify-between">
                  <span className="text-slate-500">Payment ID:</span>
                  <span className="text-teal-300 font-semibold">{gatewayRecord.recordReference}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-500">Order ID:</span>
                  <span>{gatewayRecord.orderReference}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-500">Settlement UTR:</span>
                  <span className="text-slate-200">{gatewayRecord.secondaryReference || '—'}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-500">Net Settled:</span>
                  <span className="text-slate-100 font-bold">₹{formatMinorUnits(gatewayRecord.netAmountMinor)}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-500">Settled At:</span>
                  <span className="text-slate-400">
                    {gatewayRecord.settlementTimestamp ? new Date(gatewayRecord.settlementTimestamp).toLocaleString() : '—'}
                  </span>
                </div>
              </div>
            ) : (
              <div className="text-xs text-rose-400 font-mono py-2">
                [No settlement record found in Razorpay batch]
              </div>
            )}
          </div>

          {/* 3. Bank Statement Card */}
          <div className="bg-[#111827] border border-[#1E293B] rounded-xl p-4">
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs font-mono font-bold text-emerald-400 uppercase">3. Bank Statement (HDFC)</span>
              <span className="text-[10px] font-mono text-slate-500">{bankRecord?.id || '—'}</span>
            </div>
            {bankRecord ? (
              <div className="space-y-1.5 text-xs font-mono text-slate-300">
                <div className="flex justify-between">
                  <span className="text-slate-500">Bank Txn ID:</span>
                  <span>{bankRecord.recordReference}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-500">Credit Amount:</span>
                  <span className="text-emerald-400 font-bold">₹{formatMinorUnits(bankRecord.amountMinor)}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-500">Ref UTR:</span>
                  <span>{bankRecord.secondaryReference || '—'}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-500">Value Date:</span>
                  <span className="text-slate-400">{new Date(bankRecord.transactionTimestamp).toLocaleString()}</span>
                </div>
              </div>
            ) : (
              <div className="text-xs text-rose-400 font-mono py-2">
                [No corresponding credit detected in bank statement]
              </div>
            )}
          </div>
        </div>

        {/* CENTRE COLUMN: AI Investigation Workspace (5 cols) */}
        <div className="lg:col-span-5 space-y-4">
          <div className="bg-[#111827] border border-[#1E293B] rounded-xl p-5 relative overflow-hidden">
            <div className="flex items-center justify-between mb-4 pb-3 border-b border-[#1E293B]">
              <div className="flex items-center gap-2.5">
                <div className="p-2 rounded-lg bg-teal-500/10 text-teal-400 border border-teal-500/20">
                  <Sparkles className="w-5 h-5" />
                </div>
                <div>
                  <h2 className="text-sm font-bold text-slate-100">AI Investigation</h2>
                  <span className="text-[10px] text-slate-500 font-mono">
                    Model: {investigation?.modelVersion || 'gemini-1.5-flash'} | {investigation?.promptVersion || 'v2.0'}
                  </span>
                </div>
              </div>

              {investigation && (
                <div className="text-right">
                  <span className="text-xs font-mono font-bold text-teal-300">
                    {investigation.confidenceScore > 0 ? `${investigation.confidenceScore}%` : 'Not calibrated'}
                  </span>
                  <span className="text-[10px] text-slate-500 block font-mono">Confidence score</span>
                </div>
              )}
            </div>

            {investigation ? (
              <div className="space-y-5">
                {/* Executive Summary */}
                <div>
                  <span className="text-[10px] font-mono uppercase font-semibold text-slate-400 block mb-1">
                    Financial Diagnosis & Summary
                  </span>
                  <p className="text-xs text-slate-200 leading-relaxed bg-[#0B0F19] p-3 rounded-lg border border-[#1E293B]">
                    {investigation.summary}
                  </p>
                </div>

                {/* Possible Root Causes */}
                {parsedCauses.length > 0 && (
                  <div>
                    <span className="text-[10px] font-mono uppercase font-semibold text-slate-400 block mb-1.5">
                      Identified Potential Root Causes
                    </span>
                    <ul className="space-y-1.5 text-xs text-slate-300">
                      {parsedCauses.map((cause, idx) => (
                        <li key={idx} className="flex items-start gap-2 bg-[#0F172A] p-2 rounded border border-[#1E293B]">
                          <span className="w-1.5 h-1.5 rounded-full bg-teal-400 mt-1.5 shrink-0" />
                          <span>{cause}</span>
                        </li>
                      ))}
                    </ul>
                  </div>
                )}

                {/* Supporting Grounded Evidence */}
                {parsedEvidence.length > 0 && (
                  <div>
                    <span className="text-[10px] font-mono uppercase font-semibold text-slate-400 block mb-1.5">
                      Corroborating Evidence Grounding
                    </span>
                    <div className="space-y-1.5 text-xs font-mono text-slate-400">
                      {parsedEvidence.map((ev, idx) => (
                        <div key={idx} className="p-2 rounded bg-[#0F172A] border border-[#1E293B]">
                          {ev}
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* Action Reasoning */}
                <div>
                  <span className="text-[10px] font-mono uppercase font-semibold text-slate-400 block mb-1">
                    Controller Action Rationale
                  </span>
                  <p className="text-xs text-slate-300 leading-relaxed bg-[#0F172A] p-3 rounded-lg border border-[#1E293B]">
                    {investigation.actionReasoning}
                  </p>
                </div>

                {/* Missing Information Note */}
                {investigation.missingEvidence && investigation.missingEvidence !== 'None' && (
                  <div className="bg-amber-950/20 border border-amber-800/40 p-3 rounded-lg text-xs text-amber-300">
                    <span className="font-semibold block mb-0.5">Missing Documentation / Evidence:</span>
                    <span className="text-amber-200/90">{investigation.missingEvidence}</span>
                  </div>
                )}

                {/* Safety Guarantee Pill */}
                <div className="p-2.5 rounded-lg bg-teal-950/40 border border-teal-800/40 text-[11px] text-teal-300 flex items-center gap-2">
                  <ShieldCheck className="w-4 h-4 text-teal-400 shrink-0" />
                  <span>Deterministic Invariant: AI cannot modify ledger truth or auto-approve resolutions.</span>
                </div>
              </div>
            ) : (
              <div className="py-16 text-center space-y-3">
                <Sparkles className="w-8 h-8 text-slate-600 mx-auto" />
                <div className="text-xs text-slate-400">
                  This discrepancy has not been investigated by the AI controller yet.
                </div>
                <button
                  onClick={handleTriggerInvestigation}
                  disabled={investigating}
                  className="px-4 py-2 bg-teal-600 hover:bg-teal-500 text-white rounded-lg text-xs font-semibold shadow-md inline-flex items-center gap-2 transition-all"
                >
                  {investigating && <RefreshCw className="w-3.5 h-3.5 animate-spin" />}
                  <span>Run AI Investigation Now</span>
                </button>
              </div>
            )}
          </div>
        </div>

        {/* RIGHT COLUMN: Human Approval & Resolution Actions (3 cols) */}
        <div className="lg:col-span-3 space-y-4">
          <div className="bg-[#111827] border border-[#1E293B] rounded-xl p-4">
            <h2 className="text-xs font-mono uppercase font-semibold text-slate-300 mb-3 flex items-center gap-2">
              <CheckCircle2 className="w-4 h-4 text-teal-400" />
              <span>Recommended Resolution</span>
            </h2>

            <div className="bg-[#0F172A] border border-[#1E293B] rounded-lg p-3 text-xs mb-4">
              <span className="text-[10px] text-slate-500 font-mono uppercase block mb-1">Controller Action</span>
              <span className="text-teal-300 font-bold font-mono text-sm block">
                {investigation?.recommendedAction || 'APPROVE_ADJUSTMENT'}
              </span>
              <span className="text-[11px] text-slate-400 mt-1 block">
                Requires authorized finance controller sign-off.
              </span>
            </div>

            {/* Action Buttons */}
            <div className="space-y-2">
              <button
                onClick={() =>
                  setActionModal({
                    isOpen: true,
                    actionType: investigation?.recommendedAction || 'APPROVE_ADJUSTMENT',
                    title: 'Approve Financial Resolution',
                    description: `Authorize financial adjustment for exception ${discrepancy.id}. An auditable adjustment entry will be created.`,
                  })
                }
                className="w-full py-2 px-3 bg-emerald-600 hover:bg-emerald-500 text-white rounded-lg text-xs font-semibold shadow-sm transition-colors flex items-center justify-center gap-2"
              >
                <CheckCircle2 className="w-4 h-4" />
                <span>Approve Recommended Action</span>
              </button>

              <button
                onClick={() =>
                  setActionModal({
                    isOpen: true,
                    actionType: 'REJECT_INVESTIGATION',
                    title: 'Reject AI Investigation',
                    description: `Reject the AI recommendation for ${discrepancy.id}. Re-opens discrepancy for manual controller review.`,
                  })
                }
                className="w-full py-2 px-3 bg-[#0F172A] hover:bg-rose-950/40 border border-[#1E293B] hover:border-rose-700 text-rose-300 rounded-lg text-xs font-semibold transition-colors flex items-center justify-center gap-2"
              >
                <XCircle className="w-4 h-4" />
                <span>Reject Recommendation</span>
              </button>

              <button
                onClick={() =>
                  setActionModal({
                    isOpen: true,
                    actionType: 'REQUEST_EVIDENCE',
                    title: 'Request Additional Counterparty Evidence',
                    description: `Request further clarification or nodal payment receipts from Razorpay operations or bank operations.`,
                  })
                }
                className="w-full py-2 px-3 bg-[#0F172A] hover:bg-slate-800 border border-[#1E293B] text-slate-300 rounded-lg text-xs font-semibold transition-colors flex items-center justify-center gap-2"
              >
                <FileQuestion className="w-4 h-4" />
                <span>Request Evidence</span>
              </button>
            </div>
          </div>

          {/* Action History on this Discrepancy */}
          <div className="bg-[#111827] border border-[#1E293B] rounded-xl p-4">
            <h2 className="text-xs font-mono uppercase font-semibold text-slate-300 mb-3">
              Resolution History ({actions.length})
            </h2>

            {actions.length > 0 ? (
              <div className="space-y-2 text-xs font-mono">
                {actions.map((act) => (
                  <div key={act.id} className="p-2.5 rounded bg-[#0F172A] border border-[#1E293B]">
                    <div className="flex justify-between text-teal-300 font-bold">
                      <span>{act.actionType}</span>
                      <StatusBadge status={act.status} size="sm" />
                    </div>
                    <span className="text-[10px] text-slate-500 block mt-1">
                      By {act.actor} at {new Date(act.appliedAt).toLocaleString()}
                    </span>
                    {act.decisionNotes && (
                      <p className="text-slate-300 text-[11px] font-sans mt-1">
                        "{act.decisionNotes}"
                      </p>
                    )}
                  </div>
                ))}
              </div>
            ) : (
              <div className="text-xs text-slate-500 font-mono py-4 text-center">
                No human actions recorded yet.
              </div>
            )}
          </div>

          {/* Linked Immutable Audit Trail */}
          <div className="bg-[#111827] border border-[#1E293B] rounded-xl p-4">
            <h2 className="text-xs font-mono uppercase font-semibold text-slate-300 mb-3 flex items-center gap-2">
              <ShieldCheck className="w-4 h-4 text-teal-400" />
              <span>Audit Trail ({auditTrail.length})</span>
            </h2>

            {auditTrail.length > 0 ? (
              <div className="space-y-2 text-[11px] font-mono">
                {auditTrail.map((ev) => (
                  <div key={ev.id} className="p-2 rounded bg-[#0B0F19] border border-[#1E293B]">
                    <div className="text-slate-300 font-semibold">{ev.action}</div>
                    <span className="text-[10px] text-slate-500 block">
                      Actor: {ev.actor} | {new Date(ev.createdAt).toLocaleTimeString()}
                    </span>
                    {ev.afterState && (
                      <span className="text-[10px] text-slate-400 block truncate" title={ev.afterState}>
                        State: {ev.afterState}
                      </span>
                    )}
                  </div>
                ))}
              </div>
            ) : (
              <div className="text-xs text-slate-500 font-mono py-2 text-center">
                No audit events.
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Confirmation Modal */}
      <ConfirmationModal
        isOpen={actionModal.isOpen}
        onClose={() => setActionModal((prev) => ({ ...prev, isOpen: false }))}
        onConfirm={handleExecuteAction}
        actionType={actionModal.actionType}
        title={actionModal.title}
        description={actionModal.description}
        defaultAdjustmentMinor={discrepancy.amountMinor}
        loading={modalLoading}
      />
    </div>
  );
};
