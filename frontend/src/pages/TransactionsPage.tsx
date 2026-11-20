import React, { useState, useEffect } from 'react';
import {
  Search,
  Receipt,
  Layers,
  ArrowRight,
  X,
  Clock,
  ShieldCheck,
  CheckCircle2,
  AlertTriangle,
  RefreshCw
} from 'lucide-react';
import { api } from '../services/api';
import { FinanceRecord, FinanceSourceType } from '../types';
import { StatusBadge } from '../components/StatusBadge';
import { formatMinorUnits } from '../utils/currency';

export const TransactionsPage: React.FC = () => {
  const [records, setRecords] = useState<FinanceRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [sourceFilter, setSourceFilter] = useState<FinanceSourceType | ''>('');
  const [statusFilter, setStatusFilter] = useState<string>('');
  const [searchQuery, setSearchQuery] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [timelineError, setTimelineError] = useState<string | null>(null);

  // Selected Transaction for Timeline Modal
  const [selectedRecordId, setSelectedRecordId] = useState<string | null>(null);
  const [timelineData, setTimelineData] = useState<any | null>(null);
  const [timelineLoading, setTimelineLoading] = useState(false);

  const fetchRecords = async () => {
    try {
      setLoading(true);
      const res = await api.searchTransactions(
        page,
        25,
        searchQuery || undefined,
        sourceFilter || undefined,
        statusFilter || undefined
      );
      setRecords(res.content);
      setTotalPages(res.totalPages);
      setError(null);
    } catch (fetchError) {
      setError(fetchError instanceof Error ? fetchError.message : 'Unable to load transactions.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchRecords();
  }, [page, sourceFilter, statusFilter]);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    fetchRecords();
  };

  const openTimeline = async (id: string) => {
    setSelectedRecordId(id);
    try {
      setTimelineLoading(true);
      setTimelineError(null);
      const data = await api.getTransactionTimeline(id);
      setTimelineData(data);
    } catch (fetchError) {
      setTimelineError(fetchError instanceof Error ? fetchError.message : 'Unable to load transaction timeline.');
    } finally {
      setTimelineLoading(false);
    }
  };

  return (
    <div className="space-y-8 animate-in fade-in duration-200">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 border-b border-[#1E293B] pb-6">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-100">Financial Transaction Explorer</h1>
          <p className="text-sm text-slate-400 mt-1">
            Searchable financial ledger records across Ledger, Gateway, and Bank statement sources.
          </p>
        </div>

        <button
          onClick={fetchRecords}
          className="p-2 rounded-lg bg-[#111827] border border-[#1E293B] hover:border-slate-600 text-slate-400 hover:text-white transition-colors self-start md:self-auto"
        >
          <RefreshCw className="w-4 h-4" />
        </button>
      </div>

      {error && (
        <div role="alert" className="rounded-lg border border-rose-800 bg-rose-950/30 p-3 text-sm text-rose-300">
          {error}
          <button onClick={fetchRecords} className="ml-3 underline">Retry</button>
        </div>
      )}

      {/* Filter and Search Bar */}
      <div className="bg-[#111827] border border-[#1E293B] rounded-xl p-4 flex flex-col md:flex-row md:items-center justify-between gap-4">
        <form onSubmit={handleSearch} className="relative flex-1 max-w-md">
          <Search className="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
          <input
            type="text"
            placeholder="Search by Payment ID, Order ID, UTR..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full pl-9 pr-4 py-1.5 bg-[#0F172A] border border-[#1E293B] rounded-lg text-xs text-slate-100 focus:outline-none focus:border-teal-500 transition-colors font-mono"
          />
        </form>

        <div className="flex items-center gap-3">
          <select
            value={sourceFilter}
            onChange={(e) => {
              setSourceFilter(e.target.value as FinanceSourceType);
              setPage(0);
            }}
            className="bg-[#0F172A] border border-[#1E293B] rounded-lg px-3 py-1.5 text-xs text-slate-300 font-mono focus:outline-none focus:border-teal-500"
          >
            <option value="">All Sources</option>
            <option value="LEDGER">LEDGER</option>
            <option value="RAZORPAY">RAZORPAY</option>
            <option value="BANK">BANK</option>
          </select>

          <select
            value={statusFilter}
            onChange={(e) => {
              setStatusFilter(e.target.value);
              setPage(0);
            }}
            className="bg-[#0F172A] border border-[#1E293B] rounded-lg px-3 py-1.5 text-xs text-slate-300 font-mono focus:outline-none focus:border-teal-500"
          >
            <option value="">All Statuses</option>
            <option value="MATCHED">MATCHED</option>
            <option value="UNRECONCILED">UNRECONCILED</option>
            <option value="PARTIALLY_MATCHED">PARTIALLY_MATCHED</option>
            <option value="DISCREPANCY">DISCREPANCY</option>
          </select>
        </div>
      </div>

      {/* Transactions Table */}
      <div className="bg-[#111827] border border-[#1E293B] rounded-xl p-5">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead className="bg-[#0F172A] text-slate-400 uppercase font-mono border-b border-[#1E293B]">
              <tr>
                <th className="py-2.5 px-4 font-semibold">Record Ref</th>
                <th className="py-2.5 px-4 font-semibold">Source</th>
                <th className="py-2.5 px-4 font-semibold">Order ID</th>
                <th className="py-2.5 px-4 font-semibold">Gross (INR)</th>
                <th className="py-2.5 px-4 font-semibold">Net (INR)</th>
                <th className="py-2.5 px-4 font-semibold">Timestamp</th>
                <th className="py-2.5 px-4 font-semibold">Status</th>
                <th className="py-2.5 px-4 font-semibold text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#1E293B] font-mono text-slate-300">
              {records.length > 0 ? (
                records.map((r) => (
                  <tr key={r.id} className="hover:bg-[#1E293B]/30 transition-colors">
                    <td className="py-3 px-4 font-bold text-teal-300">
                      {r.recordReference}
                      {r.secondaryReference && (
                        <span className="block text-[10px] text-slate-500 font-normal">
                          UTR: {r.secondaryReference}
                        </span>
                      )}
                    </td>
                    <td className="py-3 px-4">
                      <span className="px-2 py-0.5 rounded bg-[#0F172A] border border-[#1E293B] text-[11px] font-semibold text-slate-300">
                        {r.sourceType}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-slate-200 font-semibold">{r.orderReference || '—'}</td>
                    <td className="py-3 px-4 text-slate-100 font-bold">
                      ₹{formatMinorUnits(r.amountMinor)}
                    </td>
                    <td className="py-3 px-4 text-slate-400">
                      ₹{formatMinorUnits(r.netAmountMinor)}
                    </td>
                    <td className="py-3 px-4 text-slate-400 font-sans">
                      {new Date(r.transactionTimestamp).toLocaleString()}
                    </td>
                    <td className="py-3 px-4">
                      <StatusBadge status={r.status} size="sm" />
                    </td>
                    <td className="py-3 px-4 text-right">
                      <button
                        onClick={() => openTimeline(r.id)}
                        className="px-2.5 py-1 bg-[#0F172A] border border-[#1E293B] hover:border-teal-500 text-teal-400 hover:text-white rounded text-xs font-sans inline-flex items-center gap-1 transition-colors"
                      >
                        <span>Timeline</span>
                        <ArrowRight className="w-3 h-3" />
                      </button>
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={8} className="py-8 text-center text-slate-500 font-sans">
                    No records found matching current criteria.
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

      {/* Transaction Timeline Slideover / Modal */}
      {selectedRecordId && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm animate-in fade-in duration-150">
          <div className="bg-[#111827] border border-[#1E293B] rounded-xl max-w-2xl w-full p-6 shadow-2xl relative max-h-[85vh] overflow-y-auto">
            <button
              onClick={() => {
                setSelectedRecordId(null);
                setTimelineData(null);
                setTimelineError(null);
              }}
              className="absolute top-4 right-4 text-slate-400 hover:text-white transition-colors"
            >
              <X className="w-5 h-5" />
            </button>

            <div className="flex items-center gap-3 mb-4">
              <div className="p-2.5 rounded-lg bg-teal-950/60 text-teal-400 border border-teal-800">
                <Receipt className="w-5 h-5" />
              </div>
              <div>
                <h3 className="text-base font-bold text-slate-100">Financial Transaction Timeline</h3>
                <span className="text-xs font-mono text-teal-300">
                  {timelineData?.primaryRecord?.recordReference}
                </span>
              </div>
            </div>

            {timelineLoading ? (
              <div className="py-12 text-center text-slate-400 font-mono text-xs">
                Loading linked financial evidence...
              </div>
            ) : timelineError ? (
              <div role="alert" className="py-8 text-center text-rose-300 font-mono text-xs">
                {timelineError}
                <button onClick={() => openTimeline(selectedRecordId)} className="ml-3 underline">Retry</button>
              </div>
            ) : timelineData ? (
              <div className="space-y-6">
                {/* Primary Record Summary */}
                <div className="bg-[#0B0F19] border border-[#1E293B] rounded-xl p-4 font-mono text-xs space-y-2">
                  <div className="flex justify-between">
                    <span className="text-slate-400">Order Reference:</span>
                    <span className="text-slate-200 font-bold">{timelineData.primaryRecord.orderReference || '—'}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-slate-400">Source Type:</span>
                    <span className="text-teal-400 font-bold">{timelineData.primaryRecord.sourceType}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-slate-400">Gross Amount:</span>
                    <span className="text-slate-100 font-bold">₹{formatMinorUnits(timelineData.primaryRecord.amountMinor)}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-slate-400">Deducted Fee / Tax:</span>
                    <span className="text-slate-400">
                      ₹{formatMinorUnits(timelineData.primaryRecord.feeMinor)} / ₹{formatMinorUnits(timelineData.primaryRecord.taxMinor)}
                    </span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-slate-400">Timestamp:</span>
                    <span className="text-slate-300">{new Date(timelineData.primaryRecord.transactionTimestamp).toLocaleString()}</span>
                  </div>
                </div>

                {/* Related Cross-Source Records */}
                <div>
                  <h4 className="text-xs uppercase font-mono font-semibold text-slate-400 mb-3">
                    Cross-Source Linked Evidence ({timelineData.relatedRecords.length} records)
                  </h4>
                  <div className="space-y-2 font-mono text-xs">
                    {timelineData.relatedRecords.map((rel: FinanceRecord) => (
                      <div
                        key={rel.id}
                        className="bg-[#0F172A] border border-[#1E293B] rounded-lg p-3 flex items-center justify-between"
                      >
                        <div>
                          <span className="px-1.5 py-0.5 rounded bg-slate-800 text-[10px] text-teal-300 mr-2">
                            {rel.sourceType}
                          </span>
                          <span className="text-slate-200">{rel.recordReference}</span>
                          <span className="text-[10px] text-slate-500 block">
                            {new Date(rel.transactionTimestamp).toLocaleString()}
                          </span>
                        </div>
                        <div className="text-right">
                          <span className="text-slate-100 font-bold">₹{formatMinorUnits(rel.amountMinor)}</span>
                          <span className="block">
                            <StatusBadge status={rel.status} size="sm" />
                          </span>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>

                {/* Linked Discrepancies if any */}
                {timelineData.discrepancies?.length > 0 && (
                  <div>
                    <h4 className="text-xs uppercase font-mono font-semibold text-rose-400 mb-2">
                      Linked Exceptions & Discrepancies
                    </h4>
                    {timelineData.discrepancies.map((d: any) => (
                      <div key={d.id} className="bg-rose-950/30 border border-rose-800/60 rounded-lg p-3 text-xs font-mono">
                        <div className="flex justify-between text-rose-300 font-bold mb-1">
                          <span>{d.type}</span>
                          <span>₹{formatMinorUnits(d.amountMinor)}</span>
                        </div>
                        <p className="text-slate-400 text-[11px] font-sans">{d.resolutionNotes || 'Open discrepancy awaiting investigation.'}</p>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            ) : null}
          </div>
        </div>
      )}
    </div>
  );
};
