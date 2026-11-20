import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  ArrowLeft,
  Download,
  Search,
  CheckCircle2,
  Clock,
  Layers,
  ShieldCheck,
  Scale,
  RefreshCw,
  FileSpreadsheet
} from 'lucide-react';
import { api } from '../services/api';
import { ReconciliationRun, ReconciliationMatch, MatchType } from '../types';
import { StatusBadge } from '../components/StatusBadge';
import { formatMinorUnits } from '../utils/currency';

export const RunDetailsPage: React.FC = () => {
  const { runId } = useParams<{ runId: string }>();
  const navigate = useNavigate();

  const [run, setRun] = useState<ReconciliationRun | null>(null);
  const [matches, setMatches] = useState<ReconciliationMatch[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [matchTypeFilter, setMatchTypeFilter] = useState<MatchType | ''>('');
  const [searchQuery, setSearchQuery] = useState('');

  const loadData = async () => {
    if (!runId) return;
    try {
      setLoading(true);
      const [r, m] = await Promise.all([
        api.getRun(runId),
        api.getRunMatches(runId, page, 25, matchTypeFilter || undefined, searchQuery || undefined),
      ]);
      setRun(r);
      setMatches(m.content);
      setTotalPages(m.totalPages);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, [runId, page, matchTypeFilter]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    loadData();
  };

  return (
    <div className="space-y-8 animate-in fade-in duration-200">
      {/* Top Navigation */}
      <div className="flex items-center justify-between border-b border-[#1E293B] pb-5">
        <div className="flex items-center gap-3">
          <button
            onClick={() => navigate('/reconciliation')}
            className="p-2 rounded-lg bg-[#111827] border border-[#1E293B] text-slate-400 hover:text-white transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
          </button>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-xl font-bold tracking-tight text-slate-100">Run Details</h1>
              <span className="text-xs font-mono px-2 py-0.5 rounded bg-teal-950/80 border border-teal-800 text-teal-300">
                {runId}
              </span>
            </div>
            <span className="text-xs text-slate-400 font-mono">
              Started: {run ? new Date(run.startedAt).toLocaleString() : '—'}
            </span>
          </div>
        </div>

        {runId && (
          <a
            href={api.exportRunMatchesUrl(runId)}
            target="_blank"
            rel="noreferrer"
            className="flex items-center gap-2 px-3.5 py-2 bg-[#111827] border border-[#1E293B] hover:border-slate-600 rounded-lg text-xs font-medium text-slate-200 transition-colors"
          >
            <Download className="w-4 h-4 text-teal-400" />
            <span>Export CSV</span>
          </a>
        )}
      </div>

      {/* Metrics Row */}
      <div className="grid grid-cols-2 md:grid-cols-5 gap-4">
        <div className="bg-[#111827] border border-[#1E293B] rounded-xl p-4">
          <span className="text-[11px] text-slate-400 uppercase font-mono block mb-1">Match Rate</span>
          <div className="text-2xl font-bold font-mono text-teal-400">{run ? `${run.matchRate}%` : '—'}</div>
          <span className="text-[10px] text-slate-500 font-mono">3-way validated</span>
        </div>

        <div className="bg-[#111827] border border-[#1E293B] rounded-xl p-4">
          <span className="text-[11px] text-slate-400 uppercase font-mono block mb-1">Precision</span>
          <div className="text-2xl font-bold font-mono text-slate-400">
            {run?.precisionRate != null ? `${run.precisionRate}%` : 'Not evaluated'}
          </div>
          <span className="text-[10px] text-slate-500 font-mono">Requires labeled ground truth</span>
        </div>

        <div className="bg-[#111827] border border-[#1E293B] rounded-xl p-4">
          <span className="text-[11px] text-slate-400 uppercase font-mono block mb-1">Matched Records</span>
          <div className="text-2xl font-bold font-mono text-slate-100">{run ? run.matchedRecordsCount : '—'}</div>
          <span className="text-[10px] text-slate-500 font-mono">Confirmed three-source matches</span>
        </div>

        <div className="bg-[#111827] border border-[#1E293B] rounded-xl p-4">
          <span className="text-[11px] text-slate-400 uppercase font-mono block mb-1">Unmatched Exceptions</span>
          <div className="text-2xl font-bold font-mono text-rose-400">{run ? run.discrepanciesCount : '—'}</div>
          <span className="text-[10px] text-slate-500 font-mono">Discrepancy queue</span>
        </div>

        <div className="bg-[#111827] border border-[#1E293B] rounded-xl p-4">
          <span className="text-[11px] text-slate-400 uppercase font-mono block mb-1">Latency / Duration</span>
          <div className="text-2xl font-bold font-mono text-sky-400">{run ? `${run.processingDurationMs} ms` : '—'}</div>
          <span className="text-[10px] text-slate-500 font-mono">In-memory matching</span>
        </div>
      </div>

      {/* Filter and Search Bar */}
      <div className="bg-[#111827] border border-[#1E293B] rounded-xl p-4 flex flex-col md:flex-row md:items-center justify-between gap-4">
        <form onSubmit={handleSearchSubmit} className="relative flex-1 max-w-md">
          <Search className="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
          <input
            type="text"
            placeholder="Search by Payment ID, Order ID, or UTR..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full pl-9 pr-4 py-1.5 bg-[#0F172A] border border-[#1E293B] rounded-lg text-xs text-slate-100 focus:outline-none focus:border-teal-500 transition-colors font-mono"
          />
        </form>

        <div className="flex items-center gap-3">
          <select
            value={matchTypeFilter}
            onChange={(e) => {
              setMatchTypeFilter(e.target.value as MatchType);
              setPage(0);
            }}
            className="bg-[#0F172A] border border-[#1E293B] rounded-lg px-3 py-1.5 text-xs text-slate-300 font-mono focus:outline-none focus:border-teal-500"
          >
            <option value="">All Match Types</option>
            <option value="EXACT_THREE_WAY">EXACT_THREE_WAY</option>
            <option value="TWO_WAY_LEDGER_GATEWAY">TWO_WAY_LEDGER_GATEWAY</option>
            <option value="PARTIAL_SETTLEMENT">PARTIAL_SETTLEMENT</option>
            <option value="COMPOSITE">COMPOSITE</option>
          </select>
        </div>
      </div>

      {/* Matched Records Table */}
      <div className="bg-[#111827] border border-[#1E293B] rounded-xl p-5">
        <h2 className="text-sm font-semibold text-slate-100 mb-4">Confirmed 3-Way Matches</h2>
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead className="bg-[#0F172A] text-slate-400 uppercase font-mono border-b border-[#1E293B]">
              <tr>
                <th className="py-2.5 px-4 font-semibold">Match ID</th>
                <th className="py-2.5 px-4 font-semibold">Type</th>
                <th className="py-2.5 px-4 font-semibold">Ledger Gross</th>
                <th className="py-2.5 px-4 font-semibold">Razorpay Net (Fee/Tax)</th>
                <th className="py-2.5 px-4 font-semibold">Bank Credit</th>
                <th className="py-2.5 px-4 font-semibold">Confidence</th>
                <th className="py-2.5 px-4 font-semibold">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#1E293B] font-mono text-slate-300">
              {matches.length > 0 ? (
                matches.map((m) => (
                  <tr key={m.id} className="hover:bg-[#1E293B]/30 transition-colors">
                    <td className="py-3 px-4">
                      <span className="font-bold text-teal-300">{m.id.slice(0, 16)}</span>
                      <span className="block text-[10px] text-slate-500 font-mono">
                        {m.ledgerRecord?.orderReference || m.gatewayRecord?.orderReference || '—'}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-slate-300 text-[11px]">{m.matchType}</td>
                    <td className="py-3 px-4 text-slate-200">
                      ₹{formatMinorUnits(m.ledgerAmountMinor ?? 0)}
                      <span className="block text-[10px] text-slate-500">{m.ledgerRecord?.recordReference}</span>
                    </td>
                    <td className="py-3 px-4 text-slate-200">
                      ₹{formatMinorUnits(m.gatewayAmountMinor ?? 0)}
                      <span className="block text-[10px] text-slate-500">
                        Fee: ₹{formatMinorUnits(m.feeMinor ?? 0)} | Tax: ₹{formatMinorUnits(m.taxMinor ?? 0)}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-emerald-400 font-bold">
                      ₹{formatMinorUnits(m.bankAmountMinor ?? 0)}
                      <span className="block text-[10px] text-slate-500 font-normal">{m.bankRecord?.recordReference}</span>
                    </td>
                    <td className="py-3 px-4 text-emerald-400 font-bold">{m.confidenceScore}%</td>
                    <td className="py-3 px-4">
                      <StatusBadge status={m.matchStatus} size="sm" />
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={7} className="py-8 text-center text-slate-500 font-sans">
                    No matched records found for this criteria.
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
