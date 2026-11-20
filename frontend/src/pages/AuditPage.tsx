import React, { useState, useEffect } from 'react';
import {
  ShieldCheck,
  Search,
  Filter,
  RefreshCw,
  Clock,
  Layers,
  ArrowRight
} from 'lucide-react';
import { api } from '../services/api';
import { AuditEvent } from '../types';

export const AuditPage: React.FC = () => {
  const [events, setEvents] = useState<AuditEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [resourceFilter, setResourceFilter] = useState('');
  const [error, setError] = useState<string | null>(null);

  const fetchAudit = async () => {
    try {
      setLoading(true);
      const res = await api.getAuditTrail(page, 25, resourceFilter || undefined);
      setEvents(res.content);
      setTotalPages(res.totalPages);
      setError(null);
    } catch (fetchError) {
      setError(fetchError instanceof Error ? fetchError.message : 'Unable to load audit events.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAudit();
  }, [page, resourceFilter]);

  return (
    <div className="space-y-8 animate-in fade-in duration-200">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 border-b border-[#1E293B] pb-6">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-bold tracking-tight text-slate-100">Audit Trail</h1>
            <span className="text-xs font-mono px-2 py-0.5 rounded bg-teal-950/80 border border-teal-800 text-teal-300 font-semibold">
              Application-Recorded
            </span>
          </div>
          <p className="text-sm text-slate-400 mt-1">
            Recorded discrepancy, investigation, and controller-action events with timestamps and before/after state where available.
          </p>
        </div>

        <button
          onClick={fetchAudit}
          className="p-2 rounded-lg bg-[#111827] border border-[#1E293B] hover:border-slate-600 text-slate-400 hover:text-white transition-colors self-start md:self-auto"
        >
          <RefreshCw className="w-4 h-4" />
        </button>
      </div>

      {error && (
        <div role="alert" className="rounded-lg border border-rose-800 bg-rose-950/30 p-3 text-sm text-rose-300">
          {error}
          <button onClick={fetchAudit} className="ml-3 underline">Retry</button>
        </div>
      )}

      {/* Filter Row */}
      <div className="bg-[#111827] border border-[#1E293B] rounded-xl p-4 flex items-center justify-between">
        <span className="text-xs font-mono text-slate-400 uppercase">Filter Resource:</span>
        <select
          value={resourceFilter}
          onChange={(e) => {
            setResourceFilter(e.target.value);
            setPage(0);
          }}
          className="bg-[#0F172A] border border-[#1E293B] rounded-lg px-3 py-1.5 text-xs text-slate-300 font-mono focus:outline-none focus:border-teal-500"
        >
          <option value="">All Resource Types</option>
          <option value="DISCREPANCY">DISCREPANCY</option>
          <option value="RECONCILIATION_RUN">RECONCILIATION_RUN</option>
          <option value="PAYMENT">PAYMENT</option>
        </select>
      </div>

      {/* Audit Log Table */}
      <div className="bg-[#111827] border border-[#1E293B] rounded-xl p-5">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead className="bg-[#0F172A] text-slate-400 uppercase font-mono border-b border-[#1E293B]">
              <tr>
                <th className="py-2.5 px-4 font-semibold">Timestamp</th>
                <th className="py-2.5 px-4 font-semibold">Audit Event ID</th>
                <th className="py-2.5 px-4 font-semibold">Action</th>
                <th className="py-2.5 px-4 font-semibold">Actor</th>
                <th className="py-2.5 px-4 font-semibold">Resource</th>
                <th className="py-2.5 px-4 font-semibold">Before State</th>
                <th className="py-2.5 px-4 font-semibold">After State</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#1E293B] font-mono text-slate-300">
              {events.length > 0 ? (
                events.map((ev) => (
                  <tr key={ev.id} className="hover:bg-[#1E293B]/30 transition-colors">
                    <td className="py-3 px-4 text-slate-400 font-sans whitespace-nowrap">
                      {new Date(ev.createdAt).toLocaleString()}
                    </td>
                    <td className="py-3 px-4 font-bold text-slate-400 text-[11px]">{ev.id.slice(0, 14)}</td>
                    <td className="py-3 px-4 text-teal-300 font-semibold">{ev.action}</td>
                    <td className="py-3 px-4 text-slate-300">
                      <span className="px-1.5 py-0.5 rounded bg-[#0F172A] border border-[#1E293B] text-[10px]">
                        {ev.actor}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-slate-300">
                      {ev.resourceType} : <span className="text-slate-500">{ev.resourceId.slice(0, 14)}</span>
                    </td>
                    <td className="py-3 px-4 text-slate-500 truncate max-w-xs" title={ev.beforeState || '—'}>
                      {ev.beforeState || '—'}
                    </td>
                    <td className="py-3 px-4 text-emerald-300 truncate max-w-xs" title={ev.afterState || '—'}>
                      {ev.afterState || '—'}
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={7} className="py-8 text-center text-slate-500 font-sans">
                    No audit events recorded.
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
