import React, { useState, useEffect } from 'react';
import {
  Settings,
  Shield,
  Key,
  Database,
  Cpu,
  Trash2,
  CheckCircle2,
  RefreshCw,
  Server,
  Cloud
} from 'lucide-react';
import { api } from '../services/api';

export const SettingsPage: React.FC = () => {
  const [settings, setSettings] = useState<any | null>(null);
  const [resetting, setResetting] = useState(false);
  const [isAdmin, setIsAdmin] = useState(false);
  const [feedback, setFeedback] = useState<string | null>(null);

  const fetchSettings = async () => {
    try {
      const [data, session] = await Promise.all([api.getSettings(), api.getSession()]);
      setSettings(data);
      setIsAdmin(session.role === 'ADMIN');
    } catch (err: any) {
      setFeedback(`Could not load settings: ${err.message}`);
    }
  };

  useEffect(() => {
    fetchSettings();
  }, []);

  const handleResetDemo = async () => {
    if (!window.confirm('Remove the three demo imports and their linked matches/exceptions? Non-demo records, audit events, and reconciliation run history will be preserved. Continue?')) return;
    try {
      setResetting(true);
      const res = await api.resetDemo();
      setFeedback(res.message);
      await fetchSettings();
    } catch (err: any) {
      setFeedback(`Reset failed: ${err.message}`);
    } finally {
      setResetting(false);
    }
  };

  return (
    <div className="space-y-8 animate-in fade-in duration-200">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 border-b border-[#1E293B] pb-6">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-100">Platform Settings & Architecture</h1>
          <p className="text-sm text-slate-400 mt-1">
            Environment configuration, RBAC security profiles, Gemini integration status, and demo dataset controls.
          </p>
        </div>

        <button
          onClick={fetchSettings}
          className="p-2 rounded-lg bg-[#111827] border border-[#1E293B] hover:border-slate-600 text-slate-400 hover:text-white transition-colors self-start md:self-auto"
        >
          <RefreshCw className="w-4 h-4" />
        </button>
      </div>

      {feedback && (
        <div className="bg-[#111827] border border-teal-800 rounded-xl p-4 flex items-center justify-between text-xs font-mono text-teal-300">
          <div className="flex items-center gap-2.5">
            <CheckCircle2 className="w-4 h-4 text-teal-400 shrink-0" />
            <span>{feedback}</span>
          </div>
          <button
            onClick={() => setFeedback(null)}
            className="text-slate-400 hover:text-white uppercase font-bold text-[10px]"
          >
            Clear
          </button>
        </div>
      )}

      {/* Grid of Settings Sections */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Workspace & Security Profile */}
        <div className="bg-[#111827] border border-[#1E293B] rounded-xl p-5 space-y-4">
          <div className="flex items-center gap-2.5 pb-3 border-b border-[#1E293B]">
            <Shield className="w-4 h-4 text-teal-400" />
            <h2 className="text-sm font-semibold text-slate-100">Active Workspace & RBAC</h2>
          </div>

          <div className="space-y-3 font-mono text-xs">
            <div className="flex justify-between py-1">
              <span className="text-slate-400">Workspace:</span>
              <span className="text-slate-200 font-bold">{settings?.workspace?.name || 'LedgerSync Finance Workspace'}</span>
            </div>
            <div className="flex justify-between py-1">
              <span className="text-slate-400">Security Context:</span>
              <span className="text-teal-300 font-bold">Authenticated browser session</span>
            </div>
            <div className="flex justify-between py-1">
              <span className="text-slate-400">Authenticated Role:</span>
              <span className="px-2 py-0.5 rounded bg-teal-950 border border-teal-800 text-teal-300 font-semibold">
                {settings?.workspace?.activeRole || 'Unknown'}
              </span>
            </div>
            <div className="flex justify-between py-1">
              <span className="text-slate-400">Active Actor:</span>
              <span className="text-slate-200">{settings?.workspace?.actor || 'finance-operator'}</span>
            </div>
            <div className="flex justify-between py-1">
              <span className="text-slate-400">API Key Exposure:</span>
              <span className="text-emerald-400 font-bold">NEVER EXPOSED (Protected Server-Side)</span>
            </div>
          </div>
        </div>

        {/* Gemini AI Integration */}
        <div className="bg-[#111827] border border-[#1E293B] rounded-xl p-5 space-y-4">
          <div className="flex items-center gap-2.5 pb-3 border-b border-[#1E293B]">
            <Cpu className="w-4 h-4 text-teal-400" />
            <h2 className="text-sm font-semibold text-slate-100">Gemini AI Controller Status</h2>
          </div>

          <div className="space-y-3 font-mono text-xs">
            <div className="flex justify-between py-1">
              <span className="text-slate-400">Gemini Model:</span>
              <span className="text-slate-200 font-bold">{settings?.integrations?.geminiAi?.model || 'gemini-1.5-flash'}</span>
            </div>
            <div className="flex justify-between py-1">
              <span className="text-slate-400">Integration Status:</span>
              <span className="px-2 py-0.5 rounded bg-teal-950 border border-teal-800 text-teal-300 font-semibold">
                {settings?.integrations?.geminiAi?.status || 'UNKNOWN'}
              </span>
            </div>
            <div className="flex justify-between py-1">
              <span className="text-slate-400">Grounding & Output Validator:</span>
              <span className="text-emerald-400 font-bold">Persisted-record validation enabled</span>
            </div>
            <div className="flex justify-between py-1">
              <span className="text-slate-400">Execution Safety:</span>
              <span className="text-emerald-400 font-bold">Human approval required for actions</span>
            </div>
          </div>
        </div>

        {/* Database Record Invariants */}
        <div className="bg-[#111827] border border-[#1E293B] rounded-xl p-5 space-y-4">
          <div className="flex items-center gap-2.5 pb-3 border-b border-[#1E293B]">
            <Database className="w-4 h-4 text-teal-400" />
            <h2 className="text-sm font-semibold text-slate-100">PostgreSQL Financial Schema Counters</h2>
          </div>

          <div className="grid grid-cols-2 gap-3 font-mono text-xs">
            <div className="p-3 bg-[#0F172A] border border-[#1E293B] rounded-lg">
              <span className="text-[10px] text-slate-500 uppercase block">Finance Records</span>
              <span className="text-lg font-bold text-slate-100">{settings?.databaseStats?.totalRecords || 0}</span>
            </div>
            <div className="p-3 bg-[#0F172A] border border-[#1E293B] rounded-lg">
              <span className="text-[10px] text-slate-500 uppercase block">Reconciliation Matches</span>
              <span className="text-lg font-bold text-emerald-400">{settings?.databaseStats?.totalMatches || 0}</span>
            </div>
            <div className="p-3 bg-[#0F172A] border border-[#1E293B] rounded-lg">
              <span className="text-[10px] text-slate-500 uppercase block">Recorded Discrepancies</span>
              <span className="text-lg font-bold text-rose-400">{settings?.databaseStats?.totalDiscrepancies || 0}</span>
            </div>
            <div className="p-3 bg-[#0F172A] border border-[#1E293B] rounded-lg">
              <span className="text-[10px] text-slate-500 uppercase block">Execution Runs</span>
              <span className="text-lg font-bold text-teal-300">{settings?.databaseStats?.totalRuns || 0}</span>
            </div>
          </div>
        </div>

        {/* Demo Dataset Controls */}
        <div className="bg-[#111827] border border-[#1E293B] rounded-xl p-5 space-y-4">
          <div className="flex items-center gap-2.5 pb-3 border-b border-[#1E293B]">
            <Trash2 className="w-4 h-4 text-rose-400" />
            <h2 className="text-sm font-semibold text-slate-100">Demo Data Management</h2>
          </div>

          <p className="text-xs text-slate-400 leading-relaxed font-sans">
            Resetting removes only the three named demo imports and linked matches/exceptions. Other imported records, audit events, and reconciliation run history are preserved. Administrator access is required.
          </p>

          {isAdmin ? (
            <button
              onClick={handleResetDemo}
              disabled={resetting}
              className="w-full py-2.5 px-4 bg-rose-950/40 hover:bg-rose-900/60 border border-rose-800 text-rose-300 rounded-lg text-xs font-semibold font-mono flex items-center justify-center gap-2 transition-colors"
            >
              {resetting ? <RefreshCw className="w-4 h-4 animate-spin" /> : <Trash2 className="w-4 h-4" />}
              <span>Reset Demo Batches & Discrepancies</span>
            </button>
          ) : (
            <p className="text-xs text-slate-500 font-mono">Sign in with an administrator account to reset the demo dataset.</p>
          )}
        </div>
      </div>
    </div>
  );
};
