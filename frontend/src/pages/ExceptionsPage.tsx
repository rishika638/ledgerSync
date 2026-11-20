import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  AlertTriangle,
  Sparkles,
  Search,
  Filter,
  ArrowRight,
  RefreshCw,
  Scale,
  ShieldAlert,
  CheckCircle2
} from 'lucide-react';
import { api } from '../services/api';
import { Discrepancy, DiscrepancySeverity, DiscrepancyStatus, DiscrepancyType } from '../types';
import { StatusBadge } from '../components/StatusBadge';
import { SeverityBadge } from '../components/SeverityBadge';
import { formatMinorUnits } from '../utils/currency';

export const ExceptionsPage: React.FC = () => {
  const navigate = useNavigate();

  const [exceptions, setExceptions] = useState<Discrepancy[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [statusFilter, setStatusFilter] = useState<DiscrepancyStatus | ''>('');
  const [severityFilter, setSeverityFilter] = useState<DiscrepancySeverity | ''>('');
  const [typeFilter, setTypeFilter] = useState<DiscrepancyType | ''>('');
  const [searchQuery, setSearchQuery] = useState('');
  const [error, setError] = useState<string | null>(null);

  const fetchExceptions = async () => {
    try {
      setLoading(true);
      const res = await api.searchExceptions(
        page,
        20,
        statusFilter || undefined,
        severityFilter || undefined,
        typeFilter || undefined,
        undefined,
        searchQuery || undefined
      );
      setExceptions(res.content);
      setTotalPages(res.totalPages);
      setError(null);
    } catch (fetchError) {
      setError(fetchError instanceof Error ? fetchError.message : 'Unable to load exceptions.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchExceptions();
  }, [page, statusFilter, severityFilter, typeFilter]);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    fetchExceptions();
  };

  return (
    <div className="space-y-8 animate-in fade-in duration-200">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 border-b border-[#1E293B] pb-6">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-bold tracking-tight text-slate-100">Financial Exceptions Queue</h1>
            <span className="text-xs font-mono px-2 py-0.5 rounded bg-rose-950/80 border border-rose-800 text-rose-300">
              Human-in-the-Loop
            </span>
          </div>
          <p className="text-sm text-slate-400 mt-1">
            Deterministic discrepancies flagged for AI investigation, evidence corroboration, and controller sign-off.
          </p>
        </div>

        <button
          onClick={fetchExceptions}
          className="p-2 rounded-lg bg-[#111827] border border-[#1E293B] hover:border-slate-600 text-slate-400 hover:text-white transition-colors self-start md:self-auto"
        >
          <RefreshCw className="w-4 h-4" />
        </button>
      </div>

      {error && (
        <div role="alert" className="rounded-lg border border-rose-800 bg-rose-950/30 p-3 text-sm text-rose-300">
          {error}
          <button onClick={fetchExceptions} className="ml-3 underline">Retry</button>
        </div>
      )}

      {/* Filter and Search Bar */}
      <div className="bg-[#111827] border border-[#1E293B] rounded-xl p-4 flex flex-col md:flex-row md:items-center justify-between gap-4">
        <form onSubmit={handleSearch} className="relative flex-1 max-w-md">
          <Search className="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
          <input
            type="text"
            placeholder="Search exception ID, payment ID, or evidence..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full pl-9 pr-4 py-1.5 bg-[#0F172A] border border-[#1E293B] rounded-lg text-xs text-slate-100 focus:outline-none focus:border-teal-500 transition-colors font-mono"
          />
        </form>

        <div className="flex flex-wrap items-center gap-3">
          <select
            value={statusFilter}
            onChange={(e) => {
              setStatusFilter(e.target.value as DiscrepancyStatus);
              setPage(0);
            }}
            className="bg-[#0F172A] border border-[#1E293B] rounded-lg px-3 py-1.5 text-xs text-slate-300 font-mono focus:outline-none focus:border-teal-500"
          >
            <option value="">All Statuses</option>
            <option value="OPEN">OPEN (Uninvestigated)</option>
            <option value="AWAITING_APPROVAL">AWAITING_APPROVAL</option>
            <option value="INVESTIGATING">INVESTIGATING</option>
            <option value="RESOLVED">RESOLVED</option>
            <option value="IGNORED">IGNORED</option>
          </select>

          <select
            value={severityFilter}
            onChange={(e) => {
              setSeverityFilter(e.target.value as DiscrepancySeverity);
              setPage(0);
            }}
            className="bg-[#0F172A] border border-[#1E293B] rounded-lg px-3 py-1.5 text-xs text-slate-300 font-mono focus:outline-none focus:border-teal-500"
          >
            <option value="">All Severities</option>
            <option value="CRITICAL">CRITICAL</option>
            <option value="HIGH">HIGH</option>
            <option value="MEDIUM">MEDIUM</option>
            <option value="LOW">LOW</option>
          </select>

          <select
            value={typeFilter}
            onChange={(e) => {
              setTypeFilter(e.target.value as DiscrepancyType);
              setPage(0);
            }}
            className="bg-[#0F172A] border border-[#1E293B] rounded-lg px-3 py-1.5 text-xs text-slate-300 font-mono focus:outline-none focus:border-teal-500"
          >
            <option value="">All Exception Types</option>
            <option value="MISSING_GATEWAY">MISSING_GATEWAY</option>
            <option value="MISSING_BANK">MISSING_BANK</option>
            <option value="AMOUNT_MISMATCH">AMOUNT_MISMATCH</option>
            <option value="DUPLICATE_RECORD">DUPLICATE_RECORD</option>
            <option value="FEE_MISMATCH">FEE_MISMATCH</option>
            <option value="PARTIAL_SETTLEMENT">PARTIAL_SETTLEMENT</option>
            <option value="DELAYED_SETTLEMENT">DELAYED_SETTLEMENT</option>
            <option value="REFERENCE_MISMATCH">REFERENCE_MISMATCH</option>
            <option value="AMBIGUOUS_MATCH">AMBIGUOUS_MATCH</option>
          </select>
        </div>
      </div>

      {/* Exception Table */}
      <div className="bg-[#111827] border border-[#1E293B] rounded-xl p-5">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead className="bg-[#0F172A] text-slate-400 uppercase font-mono border-b border-[#1E293B]">
              <tr>
                <th className="py-2.5 px-4 font-semibold">Exception ID</th>
                <th className="py-2.5 px-4 font-semibold">Type</th>
                <th className="py-2.5 px-4 font-semibold">Severity</th>
                <th className="py-2.5 px-4 font-semibold">Exposure Amount</th>
                <th className="py-2.5 px-4 font-semibold">Expected / Actual</th>
                <th className="py-2.5 px-4 font-semibold">Status</th>
                <th className="py-2.5 px-4 font-semibold text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#1E293B] font-mono text-slate-300">
              {exceptions.length > 0 ? (
                exceptions.map((exc) => (
                  <tr key={exc.id} className="hover:bg-[#1E293B]/30 transition-colors">
                    <td className="py-3.5 px-4">
                      <span className="font-bold text-teal-300">{exc.id}</span>
                      <span className="block text-[10px] text-slate-500 font-sans">
                        {new Date(exc.createdAt).toLocaleString()}
                      </span>
                    </td>
                    <td className="py-3.5 px-4">
                      <span className="text-slate-100 font-bold text-[11px] block">{exc.type}</span>
                      <span className="text-[10px] text-slate-500 font-mono truncate max-w-xs block">
                        {exc.providerPaymentId || exc.paymentId || '—'}
                      </span>
                    </td>
                    <td className="py-3.5 px-4">
                      <SeverityBadge severity={exc.severity} />
                    </td>
                    <td className="py-3.5 px-4 font-bold text-rose-400">
                      ₹{formatMinorUnits(exc.amountMinor)}
                    </td>
                    <td className="py-3.5 px-4 text-slate-400 text-[11px]">
                      Exp: ₹{formatMinorUnits(exc.expectedAmountMinor)} | Act: ₹{formatMinorUnits(exc.actualAmountMinor)}
                    </td>
                    <td className="py-3.5 px-4">
                      <StatusBadge status={exc.status} size="sm" />
                    </td>
                    <td className="py-3.5 px-4 text-right">
                      <button
                        onClick={() => navigate(`/exceptions/${exc.id}`)}
                        className="px-3 py-1.5 bg-teal-600/20 hover:bg-teal-600 border border-teal-500/30 hover:border-teal-500 text-teal-300 hover:text-white rounded text-xs font-sans font-semibold inline-flex items-center gap-1.5 transition-all shadow-sm"
                      >
                        <Sparkles className="w-3.5 h-3.5 text-teal-400 group-hover:text-white" />
                        <span>Investigate Workspace</span>
                        <ArrowRight className="w-3 h-3" />
                      </button>
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={7} className="py-8 text-center text-slate-500 font-sans">
                    No exceptions found for this filter. Run reconciliation to detect discrepancies.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="flex items-center justify-between pt-4 mt-4 border-t border-[#1E293B] text-xs font-mono">
            <span className="text-slate-400">Page {page + 1} of {totalPages}</span>
            <div className="flex items-center gap-2">
              <button
                disabled={page === 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                className="px-3 py-1 bg-[#0F172A] border border-[#1E293B] disabled:opacity-40 rounded text-slate-300"
              >
                Previous
              </button>
              <button
                disabled={page >= totalPages - 1}
                onClick={() => setPage((p) => p + 1)}
                className="px-3 py-1 bg-[#0F172A] border border-[#1E293B] disabled:opacity-40 rounded text-slate-300"
              >
                Next
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
