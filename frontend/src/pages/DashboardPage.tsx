import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  CheckCircle2,
  AlertOctagon,
  Clock,
  TrendingUp,
  ShieldAlert,
  ArrowRight,
  Database,
  PlayCircle,
  FileSpreadsheet,
  Cpu,
  Layers,
  Scale
} from 'lucide-react';
import { MetricCard } from '../components/MetricCard';
import { StatusBadge } from '../components/StatusBadge';
import { api } from '../services/api';
import { MetricSummary } from '../types';
import { formatMinorUnits } from '../utils/currency';
import {
  ResponsiveContainer,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  Cell,
  LineChart,
  Line,
  CartesianGrid
} from 'recharts';

export const DashboardPage: React.FC = () => {
  const navigate = useNavigate();
  const [metrics, setMetrics] = useState<MetricSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState<string | null>(null);
  const [actionMessage, setActionMessage] = useState<string | null>(null);

  const loadMetrics = async () => {
    try {
      setLoading(true);
      const data = await api.getDashboardMetrics();
      setMetrics(data);
      setLoadError(null);
    } catch (err) {
      setLoadError(err instanceof Error ? err.message : 'Unable to load dashboard metrics.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadMetrics();
  }, []);

  const handleRunRecon = async () => {
    try {
      setActionLoading('recon');
      const run = await api.startReconciliation();
      setActionMessage(`Run ${run.id} completed with ${run.matchRate}% match rate.`);
      await loadMetrics();
    } catch (error) {
      setActionMessage(error instanceof Error ? error.message : 'Unable to run reconciliation.');
    } finally {
      setActionLoading(null);
    }
  };

  const handleLoadDemo = async () => {
    try {
      setActionLoading('demo');
      const result = await api.loadDemoDataset();
      setActionMessage(result.message);
      await loadMetrics();
    } catch (error) {
      setActionMessage(error instanceof Error ? error.message : 'Unable to load the demo dataset.');
    } finally {
      setActionLoading(null);
    }
  };

  const exceptionChartData = metrics?.exceptionDistributionByType
    ? Object.entries(metrics.exceptionDistributionByType).map(([name, count]) => ({
        name: name.replace(/_/g, ' '),
        rawName: name,
        count,
      }))
    : [];

  const trendData = metrics?.reconciliationTrends?.slice().reverse() || [];

  const COLORS = ['#14B8A6', '#0EA5E9', '#F59E0B', '#F43F5E', '#8B5CF6', '#EC4899', '#6366F1'];

  return (
    <div className="space-y-8 animate-in fade-in duration-200">
      {/* Page Title & Actions */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 border-b border-[#1E293B] pb-6">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-bold tracking-tight text-slate-100">Financial Controller Dashboard</h1>
            <span className="text-xs font-mono px-2 py-0.5 rounded bg-teal-950/80 border border-teal-800 text-teal-300">
              Latest Run
            </span>
          </div>
          <p className="text-sm text-slate-400 mt-1">
            Current database view of three-way settlement records across Merchant Ledger, Razorpay, and Bank CSV imports.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={handleLoadDemo}
            disabled={actionLoading !== null}
            className="flex items-center gap-2 px-3.5 py-2 bg-[#111827] border border-[#1E293B] hover:border-slate-600 rounded-lg text-xs font-medium text-slate-200 transition-colors"
          >
            <Database className="w-4 h-4 text-teal-400" />
            <span>{actionLoading === 'demo' ? 'Loading Demo...' : 'Load Demo Dataset'}</span>
          </button>

          <button
            onClick={() => navigate('/imports')}
            className="flex items-center gap-2 px-3.5 py-2 bg-[#111827] border border-[#1E293B] hover:border-slate-600 rounded-lg text-xs font-medium text-slate-200 transition-colors"
          >
            <FileSpreadsheet className="w-4 h-4 text-sky-400" />
            <span>Import Data</span>
          </button>

          <button
            onClick={handleRunRecon}
            disabled={actionLoading !== null}
            className="flex items-center gap-2 px-4 py-2 bg-teal-600 hover:bg-teal-500 text-white rounded-lg text-xs font-semibold shadow-lg shadow-teal-900/30 transition-all"
          >
            <PlayCircle className="w-4 h-4" />
            <span>{actionLoading === 'recon' ? 'Reconciling...' : 'Run Reconciliation'}</span>
          </button>
        </div>
      </div>

      {actionMessage && (
        <div role="status" className="rounded-lg border border-teal-900 bg-teal-950/40 px-4 py-3 text-sm text-teal-200">
          {actionMessage}
          <button
            type="button"
            onClick={() => setActionMessage(null)}
            className="ml-3 text-xs font-semibold uppercase text-teal-400 hover:text-white"
          >
            Dismiss
          </button>
        </div>
      )}
      {loadError && (
        <div role="alert" className="flex items-center justify-between rounded-lg border border-rose-900 bg-rose-950/40 px-4 py-3 text-sm text-rose-200">
          <span>Dashboard data unavailable: {loadError}</span>
          <button type="button" onClick={loadMetrics} className="font-semibold text-rose-300 hover:text-white">
            Retry
          </button>
        </div>
      )}

      {/* Primary KPI Metrics */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <MetricCard
          title="Total Ledger Transactions"
          value={metrics ? metrics.totalTransactions.toLocaleString() : '—'}
          subtitle="Merchant captured payments"
          icon={Layers}
          badge="Ledger"
          badgeColor="blue"
        />

        <MetricCard
          title="3-Way Match Rows"
          value={metrics ? metrics.matchedRecords.toLocaleString() : '—'}
          subtitle={metrics ? `${metrics.matchRate}% of latest-run ledger rows; delayed settlements are included` : 'Evaluating...'}
          icon={CheckCircle2}
          badge="Benchmark required"
          badgeColor="emerald"
        />

        <MetricCard
          title="Active Exceptions"
          value={metrics ? metrics.unmatchedRecords.toLocaleString() : '—'}
          subtitle={metrics
            ? Number.isFinite(metrics.totalExceptions)
              ? `${metrics.totalExceptions} total in latest run, including resolved items`
              : 'Total count unavailable'
            : 'Exceptions awaiting investigation'}
          icon={AlertOctagon}
          badge={metrics && metrics.unmatchedRecords > 0 ? `${metrics.unmatchedRecords} Active` : 'Zero'}
          badgeColor="rose"
        />

        <MetricCard
          title="Unmatched Financial Exposure"
          value={metrics ? `₹${formatMinorUnits(metrics.unmatchedExposureMinor)}` : '—'}
          subtitle="Paise invariant tracking"
          icon={ShieldAlert}
          badge="Net At Risk"
          badgeColor="amber"
        />
      </div>

      {/* Secondary Performance Bar */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4 bg-[#111827]/60 border border-[#1E293B] rounded-xl p-4">
        <div className="flex items-center gap-3">
          <div className="p-2.5 rounded-lg bg-teal-950/50 text-teal-400 border border-teal-800/40">
            <Scale className="w-5 h-5" />
          </div>
          <div>
            <div className="text-xs text-slate-400 font-medium">Reconciliation Precision</div>
            <div className="text-lg font-bold font-mono text-slate-100">
              {metrics?.precisionRate != null ? `${metrics.precisionRate}%` : 'Not evaluated'}
            </div>
            <div className="text-[11px] text-slate-500">Precision requires labeled ground truth</div>
          </div>
        </div>

        <div className="flex items-center gap-3 border-t md:border-t-0 md:border-l border-[#1E293B] pt-3 md:pt-0 md:pl-4">
          <div className="p-2.5 rounded-lg bg-sky-950/50 text-sky-400 border border-sky-800/40">
            <Cpu className="w-5 h-5" />
          </div>
          <div>
            <div className="text-xs text-slate-400 font-medium">Engine Throughput & Latency</div>
            <div className="text-lg font-bold font-mono text-slate-100">
              {metrics ? `${metrics.durationMs} ms` : '0 ms'}
            </div>
            <div className="text-[11px] text-slate-500">Persisted run duration, including database operations</div>
          </div>
        </div>

        <div className="flex items-center gap-3 border-t md:border-t-0 md:border-l border-[#1E293B] pt-3 md:pt-0 md:pl-4">
          <div className="p-2.5 rounded-lg bg-amber-950/50 text-amber-400 border border-amber-800/40">
            <Clock className="w-5 h-5" />
          </div>
          <div>
            <div className="text-xs text-slate-400 font-medium">Pending Gateway Settlements</div>
            <div className="text-lg font-bold font-mono text-slate-100">
              {metrics ? metrics.pendingSettlements : 0}
            </div>
            <div className="text-[11px] text-slate-500">Settlements within SLA window</div>
          </div>
        </div>
      </div>

      {/* Visual Analytics Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Exception Distribution Chart */}
        <div className="bg-[#111827] border border-[#1E293B] rounded-xl p-5 flex flex-col">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h2 className="text-sm font-semibold text-slate-100">Exception Distribution by Category</h2>
              <p className="text-xs text-slate-400">Classified root causes detected across 3-way sources</p>
            </div>
            <button
              onClick={() => navigate('/exceptions')}
              className="text-xs text-teal-400 hover:text-teal-300 font-medium flex items-center gap-1"
            >
              <span>View Queue</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </button>
          </div>

          <div className="h-64 w-full">
            {exceptionChartData.length > 0 ? (
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={exceptionChartData} layout="vertical" margin={{ left: 10, right: 20, top: 5, bottom: 5 }}>
                  <XAxis type="number" stroke="#64748B" fontSize={11} />
                  <YAxis type="category" dataKey="name" stroke="#94A3B8" fontSize={11} width={130} />
                  <Tooltip
                    contentStyle={{ backgroundColor: '#0B0F19', borderColor: '#1E293B', borderRadius: '8px', fontSize: '12px' }}
                    formatter={(val: number) => [`${val} cases`, 'Occurrences']}
                  />
                  <Bar dataKey="count" radius={[0, 4, 4, 0]}>
                    {exceptionChartData.map((_, index) => (
                      <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                    ))}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            ) : (
              <div className="h-full flex items-center justify-center text-xs text-slate-500">
                No exceptions detected. Load demo dataset or run reconciliation.
              </div>
            )}
          </div>
        </div>

        {/* Reconciliation Trend Chart */}
        <div className="bg-[#111827] border border-[#1E293B] rounded-xl p-5 flex flex-col">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h2 className="text-sm font-semibold text-slate-100">Reconciliation Match Rate Trend</h2>
              <p className="text-xs text-slate-400">Historical match rate (%) across consecutive runs</p>
            </div>
            <button
              onClick={() => navigate('/reconciliation')}
              className="text-xs text-teal-400 hover:text-teal-300 font-medium flex items-center gap-1"
            >
              <span>All Runs</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </button>
          </div>

          <div className="h-64 w-full">
            {trendData.length > 0 ? (
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={trendData} margin={{ left: 5, right: 20, top: 10, bottom: 5 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#1E293B" />
                  <XAxis
                    dataKey="date"
                    stroke="#64748B"
                    fontSize={10}
                    tickFormatter={(value) => new Date(value).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })}
                  />
                  <YAxis stroke="#64748B" fontSize={11} domain={[0, 100]} />
                  <Tooltip
                    contentStyle={{ backgroundColor: '#0B0F19', borderColor: '#1E293B', borderRadius: '8px', fontSize: '12px' }}
                    formatter={(val: any) => [`${val}%`, 'Match Rate']}
                    labelFormatter={(value) => new Date(String(value)).toLocaleString()}
                  />
                  <Line
                    type="monotone"
                    dataKey="matchRate"
                    stroke="#14B8A6"
                    strokeWidth={2}
                    dot={{ fill: '#14B8A6', r: 4 }}
                    activeDot={{ r: 6 }}
                  />
                </LineChart>
              </ResponsiveContainer>
            ) : (
              <div className="h-full flex items-center justify-center text-xs text-slate-500">
                No past runs recorded. Run your first reconciliation above.
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Recent Activity & Audit Feed */}
      <div className="bg-[#111827] border border-[#1E293B] rounded-xl p-5">
        <div className="flex items-center justify-between mb-4">
          <div>
            <h2 className="text-sm font-semibold text-slate-100">Audit & Activity Log</h2>
            <p className="text-xs text-slate-400">Recorded controller investigations and resolution actions</p>
          </div>
          <button
            onClick={() => navigate('/audit')}
            className="text-xs text-teal-400 hover:text-teal-300 font-medium flex items-center gap-1"
          >
            <span>Full Audit Trail</span>
            <ArrowRight className="w-3.5 h-3.5" />
          </button>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead className="bg-[#0F172A] text-slate-400 uppercase font-mono border-b border-[#1E293B]">
              <tr>
                <th className="py-2.5 px-4 font-semibold">Timestamp</th>
                <th className="py-2.5 px-4 font-semibold">Action</th>
                <th className="py-2.5 px-4 font-semibold">Actor</th>
                <th className="py-2.5 px-4 font-semibold">Resource</th>
                <th className="py-2.5 px-4 font-semibold">State Transition</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#1E293B] font-mono text-slate-300">
              {metrics?.recentActivity && metrics.recentActivity.length > 0 ? (
                metrics.recentActivity.slice(0, 6).map((evt) => (
                  <tr key={evt.id} className="hover:bg-[#1E293B]/30 transition-colors">
                    <td className="py-2.5 px-4 text-slate-400">
                      {new Date(evt.createdAt).toLocaleString()}
                    </td>
                    <td className="py-2.5 px-4 font-semibold text-teal-300">
                      {evt.action}
                    </td>
                    <td className="py-2.5 px-4 text-slate-400">{evt.actor}</td>
                    <td className="py-2.5 px-4 text-slate-300">
                      {evt.resourceType} : <span className="text-slate-400">{evt.resourceId.slice(0, 16)}</span>
                    </td>
                    <td className="py-2.5 px-4 text-slate-400 truncate max-w-xs" title={evt.afterState || ''}>
                      {evt.afterState || evt.beforeState || '—'}
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={5} className="py-6 text-center text-slate-500 font-sans">
                    No audit records found.
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
