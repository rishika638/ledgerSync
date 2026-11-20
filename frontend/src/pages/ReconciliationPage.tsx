import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  GitCompare,
  PlayCircle,
  RefreshCw,
  Clock,
  ArrowRight,
  ShieldCheck,
  CheckCircle2,
  AlertOctagon,
  Percent
} from 'lucide-react';
import { api } from '../services/api';
import { ReconciliationRun } from '../types';
import { StatusBadge } from '../components/StatusBadge';

export const ReconciliationPage: React.FC = () => {
  const navigate = useNavigate();
  const [runs, setRuns] = useState<ReconciliationRun[]>([]);
  const [loading, setLoading] = useState(true);
  const [running, setRunning] = useState(false);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);

  const fetchRuns = async () => {
    try {
      setLoading(true);
      const res = await api.listRuns(0, 20);
      setRuns(res.content);
      setLoadError(null);
    } catch (fetchError) {
      setLoadError(fetchError instanceof Error ? fetchError.message : 'Unable to load reconciliation runs.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchRuns();
  }, []);

  const handleStartRun = async () => {
    try {
      setRunning(true);
      setStatusMessage('Executing deterministic three-way matching engine across Ledger, Razorpay, and Bank...');
      const run = await api.startReconciliation();
      setStatusMessage(
        `Run ${run.id} completed in ${run.processingDurationMs}ms! Match rate: ${run.matchRate}%, Discrepancies: ${run.discrepanciesCount}.`
      );
      await fetchRuns();
    } catch (err: any) {
      setStatusMessage(`Reconciliation failed: ${err.message}`);
    } finally {
      setRunning(false);
    }
  };

  return (
    <div className="space-y-8 animate-in fade-in duration-200">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 border-b border-[#1E293B] pb-6">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-100">Three-Way Financial Reconciliation</h1>
          <p className="text-sm text-slate-400 mt-1">
            Deterministic rule matching across Ledger, Razorpay, and Bank statement records.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={fetchRuns}
            className="p-2 rounded-lg bg-[#111827] border border-[#1E293B] hover:border-slate-600 text-slate-400 hover:text-white transition-colors"
          >
            <RefreshCw className="w-4 h-4" />
          </button>

          <button
            onClick={handleStartRun}
            disabled={running}
            className="flex items-center gap-2 px-4 py-2 bg-teal-600 hover:bg-teal-500 text-white rounded-lg text-xs font-semibold shadow-lg shadow-teal-900/30 transition-all"
          >
            {running ? <RefreshCw className="w-4 h-4 animate-spin" /> : <PlayCircle className="w-4 h-4" />}
            <span>{running ? 'Running 3-Way Match...' : 'Start Reconciliation'}</span>
          </button>
        </div>
      </div>

      {loadError && (
        <div role="alert" className="rounded-lg border border-rose-800 bg-rose-950/30 p-3 text-sm text-rose-300">
          {loadError}
          <button onClick={fetchRuns} className="ml-3 underline">Retry</button>
        </div>
      )}

      {statusMessage && (
        <div className="bg-[#111827] border border-teal-800/60 rounded-xl p-4 flex items-center justify-between text-xs font-mono text-teal-300">
          <div className="flex items-center gap-2.5">
            <CheckCircle2 className="w-4 h-4 text-teal-400 shrink-0" />
            <span>{statusMessage}</span>
          </div>
          <button
            onClick={() => setStatusMessage(null)}
            className="text-slate-400 hover:text-white uppercase font-bold text-[10px]"
          >
            Clear
          </button>
        </div>
      )}

      {/* Runs History Table */}
      <div className="bg-[#111827] border border-[#1E293B] rounded-xl p-5">
        <div className="flex items-center justify-between mb-4">
          <div>
            <h2 className="text-sm font-semibold text-slate-100">Reconciliation Run History</h2>
            <p className="text-xs text-slate-400">Deterministic run history with source volumes and available match coverage</p>
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead className="bg-[#0F172A] text-slate-400 uppercase font-mono border-b border-[#1E293B]">
              <tr>
                <th className="py-2.5 px-4 font-semibold">Run ID</th>
                <th className="py-2.5 px-4 font-semibold">Sources Checked</th>
                <th className="py-2.5 px-4 font-semibold">Matched Records</th>
                <th className="py-2.5 px-4 font-semibold">Exceptions</th>
                <th className="py-2.5 px-4 font-semibold">Match Rate</th>
                <th className="py-2.5 px-4 font-semibold">Precision</th>
                <th className="py-2.5 px-4 font-semibold">Latency</th>
                <th className="py-2.5 px-4 font-semibold">Status</th>
                <th className="py-2.5 px-4 font-semibold text-right">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#1E293B] font-mono text-slate-300">
              {runs.length > 0 ? (
                runs.map((r) => (
                  <tr key={r.id} className="hover:bg-[#1E293B]/30 transition-colors">
                    <td className="py-3 px-4 font-bold text-teal-300">
                      {r.id}
                      <span className="block text-[10px] text-slate-500 font-normal font-sans">
                        {new Date(r.startedAt).toLocaleString()}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-slate-400">
                      L: {r.totalLedgerRecords} | G: {r.totalGatewayRecords} | B: {r.totalBankRecords}
                    </td>
                    <td className="py-3 px-4 text-emerald-400 font-bold">{r.matchedRecordsCount}</td>
                    <td className="py-3 px-4 text-rose-400 font-bold">{r.discrepanciesCount}</td>
                    <td className="py-3 px-4 text-slate-100 font-bold">
                      <span className="px-2 py-0.5 rounded bg-[#0F172A] border border-[#1E293B] text-teal-300">
                        {r.matchRate}%
                      </span>
                    </td>
                    <td className="py-3 px-4 text-slate-400 font-semibold">
                      {r.precisionRate != null ? `${r.precisionRate}%` : 'Not evaluated'}
                    </td>
                    <td className="py-3 px-4 text-slate-400">{r.processingDurationMs} ms</td>
                    <td className="py-3 px-4">
                      <StatusBadge status={r.status} size="sm" />
                    </td>
                    <td className="py-3 px-4 text-right">
                      <button
                        onClick={() => navigate(`/reconciliation/${r.id}`)}
                        className="px-3 py-1 bg-[#0F172A] border border-[#1E293B] hover:border-teal-500 text-teal-400 hover:text-white rounded text-xs font-sans inline-flex items-center gap-1 transition-colors"
                      >
                        <span>Details</span>
                        <ArrowRight className="w-3 h-3" />
                      </button>
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={9} className="py-8 text-center text-slate-500 font-sans">
                    No reconciliation runs found. Click "Start Reconciliation" above.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
