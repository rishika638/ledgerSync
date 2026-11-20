import React, { useState } from 'react';
import { Sparkles, Database, LogOut, PlayCircle, RefreshCw } from 'lucide-react';
import { api } from '../services/api';

interface Props {
  onReconciliationTriggered?: () => void;
  geminiConnected?: boolean;
  username: string;
  role: 'OPERATOR' | 'ADMIN';
  onLogout: () => Promise<void>;
}

export const Navbar: React.FC<Props> = ({ onReconciliationTriggered, geminiConnected = true, username, role, onLogout }) => {
  const [loadingDemo, setLoadingDemo] = useState(false);
  const [runningRec, setRunningRec] = useState(false);
  const [bannerMsg, setBannerMsg] = useState<string | null>(null);

  const handleLoadDemo = async () => {
    try {
      setLoadingDemo(true);
      const res = await api.loadDemoDataset();
      setBannerMsg(res.message);
      if (onReconciliationTriggered) onReconciliationTriggered();
      setTimeout(() => setBannerMsg(null), 6000);
    } catch (err: any) {
      setBannerMsg('Error loading demo: ' + err.message);
      setTimeout(() => setBannerMsg(null), 6000);
    } finally {
      setLoadingDemo(false);
    }
  };

  const handleRunReconciliation = async () => {
    try {
      setRunningRec(true);
      const run = await api.startReconciliation();
      setBannerMsg(`Reconciliation run ${run.id} completed! Matched ${run.matchedRecordsCount} items (${run.matchRate}% rate).`);
      if (onReconciliationTriggered) onReconciliationTriggered();
      setTimeout(() => setBannerMsg(null), 6000);
    } catch (err: any) {
      setBannerMsg('Error executing run: ' + err.message);
      setTimeout(() => setBannerMsg(null), 6000);
    } finally {
      setRunningRec(false);
    }
  };

  const handleLogout = async () => {
    try {
      await onLogout();
    } catch (error) {
      setBannerMsg(error instanceof Error ? `Unable to sign out: ${error.message}` : 'Unable to sign out.');
    }
  };

  return (
    <>
      <header className="h-16 border-b border-[#1E293B] bg-[#0B0F19]/80 backdrop-blur-md sticky top-0 z-40 px-8 flex items-center justify-between">
        <div className="flex min-w-0 items-center gap-4">
          <div className="flex items-center gap-1.5 text-xs text-slate-400">
            <Sparkles className="w-3.5 h-3.5 text-teal-400" />
            {/* <span>Gemini AI Controller:</span> */}
            <span className={`font-mono text-xs ${geminiConnected ? 'text-teal-400' : 'text-amber-400'}`}>
              {geminiConnected ? 'ACTIVE' : 'FALLBACK READY'}
            </span>
          </div>
        </div>

        {/* Global Action Bar */}
        <div className="flex shrink-0 items-center gap-3">
          <button
            onClick={handleLoadDemo}
            disabled={loadingDemo}
            className="flex items-center gap-2 px-3.5 py-1.5 bg-[#111827] border border-[#1E293B] hover:border-teal-500/50 hover:bg-[#1E293B]/50 rounded-lg text-xs font-medium text-slate-200 transition-all"
            title="Load 165 synthetic payments across Ledger, Razorpay, and Bank CSVs"
          >
            {loadingDemo ? (
              <RefreshCw className="w-3.5 h-3.5 animate-spin text-teal-400" />
            ) : (
              <Database className="w-3.5 h-3.5 text-teal-400" />
            )}
            <span>Load Demo Dataset</span>
          </button>

          <button
            onClick={handleRunReconciliation}
            disabled={runningRec}
            className="flex items-center gap-2 px-4 py-1.5 bg-teal-600 hover:bg-teal-500 text-white rounded-lg text-xs font-semibold shadow-md shadow-teal-900/30 transition-all"
          >
            {runningRec ? (
              <RefreshCw className="w-3.5 h-3.5 animate-spin" />
            ) : (
              <PlayCircle className="w-3.5 h-3.5" />
            )}
            <span>Run Reconciliation</span>
          </button>

          <div className="pl-3 border-l border-[#1E293B] flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-full bg-[#1E293B] border border-slate-700 flex items-center justify-center text-xs font-mono font-bold text-teal-300">
              {username.slice(0, 2).toUpperCase()}
            </div>
            <div className="text-left text-xs">
              <div className="font-medium text-slate-200 leading-none">{username}</div>
              <div className="text-[10px] text-slate-500 font-mono mt-0.5">{role}</div>
            </div>
            <button
              onClick={handleLogout}
              className="ml-1 rounded-md p-2 text-slate-400 transition-colors hover:bg-slate-800 hover:text-white"
              title="Sign out"
              aria-label="Sign out"
            >
              <LogOut className="h-4 w-4" />
            </button>
          </div>
        </div>
      </header>

      {/* Global Notification Banner */}
      {bannerMsg && (
        <div className="bg-teal-950/90 border-b border-teal-800 text-teal-200 px-8 py-2.5 text-xs font-mono flex items-center justify-between animate-in slide-in-from-top duration-200 sticky top-16 z-30">
          <div className="flex items-center gap-2">
            <Sparkles className="w-4 h-4 text-teal-400 shrink-0" />
            <span>{bannerMsg}</span>
          </div>
          <button
            onClick={() => setBannerMsg(null)}
            className="text-teal-400 hover:text-white uppercase font-bold text-[10px] px-2 py-0.5 rounded bg-teal-900/60"
          >
            Dismiss
          </button>
        </div>
      )}
    </>
  );
};
