import React, { useState, useEffect } from 'react';
import {
  UploadCloud,
  FileSpreadsheet,
  Download,
  CheckCircle,
  AlertCircle,
  Database,
  RefreshCw,
  Clock,
  Layers
} from 'lucide-react';
import { api } from '../services/api';
import { ImportBatch, FinanceSourceType } from '../types';
import { StatusBadge } from '../components/StatusBadge';

export const ImportsPage: React.FC = () => {
  const [batches, setBatches] = useState<ImportBatch[]>([]);
  const [loadingBatches, setLoadingBatches] = useState(true);
  const [uploadingSource, setUploadingSource] = useState<FinanceSourceType | null>(null);
  const [uploadFeedback, setUploadFeedback] = useState<string | null>(null);
  const [demoLoading, setDemoLoading] = useState(false);
  const [historyError, setHistoryError] = useState<string | null>(null);

  const fetchBatches = async () => {
    try {
      setLoadingBatches(true);
      const res = await api.listBatches(0, 20);
      setBatches(res.content);
      setHistoryError(null);
    } catch (error) {
      setHistoryError(error instanceof Error ? error.message : 'Unable to load import history.');
    } finally {
      setLoadingBatches(false);
    }
  };

  useEffect(() => {
    fetchBatches();
  }, []);

  const handleFileUpload = async (sourceType: FinanceSourceType, file: File) => {
    try {
      setUploadingSource(sourceType);
      setUploadFeedback(`Uploading and validating ${file.name}...`);
      const result = await api.uploadCsv(file, sourceType);
      setUploadFeedback(result.message ||
        `Batch ${result.batchId}: ${result.status}; ${result.validRows} valid and ${result.invalidRows} invalid rows.`);
      if (result.invalidRowErrors.length > 0) {
        setUploadFeedback(`${result.message || `Batch ${result.batchId} ${result.status}.`} ${result.invalidRowErrors.slice(0, 3).join(' | ')}`);
      }
      await fetchBatches();
    } catch (err: any) {
      setUploadFeedback(`Upload failed: ${err.message}`);
    } finally {
      setUploadingSource(null);
    }
  };

  const handleLoadDemo = async () => {
    try {
      setDemoLoading(true);
      setUploadFeedback('Loading 165 synthetic payments across 3 sources...');
      const res = await api.loadDemoDataset();
      setUploadFeedback(res.message);
      await fetchBatches();
    } catch (err: any) {
      setUploadFeedback(`Demo load failed: ${err.message}`);
    } finally {
      setDemoLoading(false);
    }
  };

  const sources: Array<{
    type: FinanceSourceType;
    title: string;
    subtitle: string;
    templateName: string;
    columns: string;
    color: string;
  }> = [
    {
      type: 'LEDGER',
      title: 'Merchant Payment Ledger',
      subtitle: 'Internal captured orders, gross amounts, and cart timestamps',
      templateName: 'ledger',
      columns: 'ledger_id, order_id, customer_id, amount, fee, tax, created_at, status',
      color: 'border-blue-800/40 hover:border-blue-500/50',
    },
    {
      type: 'RAZORPAY',
      title: 'Razorpay Settlements',
      subtitle: 'Aggregator payout report, MDR fees, GST tax, and settlement UTR',
      templateName: 'razorpay',
      columns: 'payment_id, settlement_id, order_id, arn_utr, gross_amount, fee, tax, net_amount',
      color: 'border-teal-800/40 hover:border-teal-500/50',
    },
    {
      type: 'BANK',
      title: 'Bank Statement (HDFC CMS)',
      subtitle: 'Bank credit transfers, value date, and counterparty narration',
      templateName: 'bank',
      columns: 'bank_txn_id, account_number, value_date, credit_amount, reference_utr',
      color: 'border-emerald-800/40 hover:border-emerald-500/50',
    },
  ];

  return (
    <div className="space-y-8 animate-in fade-in duration-200">
      {/* Page Header */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 border-b border-[#1E293B] pb-6">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-100">Financial Data Sources & Ingestion</h1>
          <p className="text-sm text-slate-400 mt-1">
            Ingest financial CSVs for three-way reconciliation. Parsed into integer minor units (paise) with validation.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={handleLoadDemo}
            disabled={demoLoading}
            className="flex items-center gap-2 px-4 py-2 bg-teal-600 hover:bg-teal-500 text-white rounded-lg text-xs font-semibold shadow-lg shadow-teal-900/30 transition-all"
          >
            {demoLoading ? <RefreshCw className="w-4 h-4 animate-spin" /> : <Database className="w-4 h-4" />}
            <span>Load Demo Dataset (165 records)</span>
          </button>
        </div>
      </div>

      {/* Upload Feedback Toast */}
      {uploadFeedback && (
        <div className="bg-[#111827] border border-teal-800/60 rounded-xl p-4 flex items-center justify-between text-xs font-mono text-teal-300">
          <div className="flex items-center gap-2.5">
            <CheckCircle className="w-4 h-4 text-teal-400 shrink-0" />
            <span>{uploadFeedback}</span>
          </div>
          <button
            onClick={() => setUploadFeedback(null)}
            className="text-slate-400 hover:text-white uppercase font-bold text-[10px]"
          >
            Clear
          </button>
        </div>
      )}

      {/* Three CSV Upload Cards */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {sources.map((s) => (
          <div
            key={s.type}
            className={`bg-[#111827] border rounded-xl p-6 flex flex-col justify-between transition-all ${s.color}`}
          >
            <div>
              <div className="flex items-center justify-between mb-3">
                <span className="text-xs font-mono uppercase px-2 py-0.5 rounded bg-[#0F172A] border border-[#1E293B] text-slate-300 font-semibold">
                  Source: {s.type}
                </span>
                <button
                  onClick={() => api.downloadTemplate(s.templateName)}
                  className="text-xs text-slate-400 hover:text-teal-400 flex items-center gap-1 font-mono transition-colors"
                  title="Download CSV sample format"
                >
                  <Download className="w-3.5 h-3.5" />
                  <span>Template</span>
                </button>
              </div>

              <h2 className="text-base font-semibold text-slate-100 mb-1">{s.title}</h2>
              <p className="text-xs text-slate-400 mb-4">{s.subtitle}</p>

              <div className="bg-[#0B0F19] border border-[#1E293B] rounded-lg p-3 text-[11px] font-mono text-slate-400 mb-6 truncate" title={s.columns}>
                <span className="text-slate-500 block text-[10px] uppercase mb-0.5">Schema Columns</span>
                {s.columns}
              </div>
            </div>

            {/* Drag & Drop Upload Zone */}
            <label className="border-2 border-dashed border-[#1E293B] hover:border-teal-500/50 rounded-xl p-6 flex flex-col items-center justify-center cursor-pointer bg-[#0F172A]/40 hover:bg-[#0F172A] transition-all group">
              <UploadCloud className="w-8 h-8 text-slate-500 group-hover:text-teal-400 transition-colors mb-2" />
              <span className="text-xs font-medium text-slate-200">
                {uploadingSource === s.type ? 'Parsing & Validating...' : 'Click to Upload CSV'}
              </span>
              <span className="text-[10px] text-slate-500 mt-1">UTF-8 encoded .csv files</span>
              <input
                type="file"
                accept=".csv"
                className="hidden"
                disabled={uploadingSource !== null}
                onChange={(e) => {
                  const file = e.target.files?.[0];
                  if (file) handleFileUpload(s.type, file);
                }}
              />
            </label>
          </div>
        ))}
      </div>

      {/* Import History Table */}
      <div className="bg-[#111827] border border-[#1E293B] rounded-xl p-5">
        {historyError && (
          <div role="alert" className="mb-3 flex items-center justify-between rounded-lg border border-rose-900 bg-rose-950/40 px-3 py-2 text-xs text-rose-200">
            <span>Import history unavailable: {historyError}</span>
            <button type="button" onClick={fetchBatches} className="font-semibold">Retry</button>
          </div>
        )}
        <div className="flex items-center justify-between mb-4">
          <div>
            <h2 className="text-sm font-semibold text-slate-100">Batch Ingestion History</h2>
            <p className="text-xs text-slate-400">Past CSV uploads with row validations and error diagnostics</p>
          </div>
          <button
            onClick={fetchBatches}
            className="p-1.5 rounded-lg bg-[#0F172A] border border-[#1E293B] hover:border-slate-600 text-slate-400 hover:text-white transition-colors"
          >
            <RefreshCw className="w-4 h-4" />
          </button>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead className="bg-[#0F172A] text-slate-400 uppercase font-mono border-b border-[#1E293B]">
              <tr>
                <th className="py-2.5 px-4 font-semibold">Batch Name / File</th>
                <th className="py-2.5 px-4 font-semibold">Source</th>
                <th className="py-2.5 px-4 font-semibold">Total Rows</th>
                <th className="py-2.5 px-4 font-semibold">Valid Rows</th>
                <th className="py-2.5 px-4 font-semibold">Invalid Rows</th>
                <th className="py-2.5 px-4 font-semibold">Status</th>
                <th className="py-2.5 px-4 font-semibold">Imported At</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#1E293B] font-mono text-slate-300">
              {batches.length > 0 ? (
                batches.map((b) => (
                  <tr key={b.id} className="hover:bg-[#1E293B]/30 transition-colors">
                    <td className="py-3 px-4">
                      <div className="font-sans font-medium text-slate-200">{b.batchName}</div>
                      <div className="text-[11px] text-slate-500 font-mono">{b.filename} ({b.id.slice(0, 12)})</div>
                      {b.errorSummary && (
                        <details className="mt-1 max-w-xs font-sans text-[10px] text-rose-300">
                          <summary className="cursor-pointer">Row diagnostics</summary>
                          <pre className="mt-1 whitespace-pre-wrap break-words">{b.errorSummary}</pre>
                        </details>
                      )}
                    </td>
                    <td className="py-3 px-4 font-semibold text-teal-300">{b.sourceType}</td>
                    <td className="py-3 px-4">{b.totalRows}</td>
                    <td className="py-3 px-4 text-emerald-400 font-bold">{b.validRows}</td>
                    <td className="py-3 px-4 text-rose-400">{b.invalidRows}</td>
                    <td className="py-3 px-4">
                      <StatusBadge status={b.status} size="sm" />
                    </td>
                    <td className="py-3 px-4 text-slate-400 font-sans">
                      {new Date(b.createdAt).toLocaleString()}
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={7} className="py-8 text-center text-slate-500 font-sans">
                    No import batches found. Upload a file above or click "Load Demo Dataset".
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
