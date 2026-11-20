import React from 'react';
import { NavLink } from 'react-router-dom';
import {
  LayoutDashboard,
  UploadCloud,
  GitCompare,
  Receipt,
  AlertTriangle,
  Bot,
  ShieldCheck,
  Settings,
  Scale
} from 'lucide-react';

interface Props {
  openExceptionsCount?: number;
}

export const Sidebar: React.FC<Props> = ({ openExceptionsCount = 0 }) => {
  const navItems = [
    { label: 'Dashboard', path: '/dashboard', icon: LayoutDashboard },
    { label: 'Data Sources', path: '/imports', icon: UploadCloud },
    { label: 'Reconciliation', path: '/reconciliation', icon: GitCompare },
    { label: 'Transactions', path: '/transactions', icon: Receipt },
    { label: 'Exceptions Queue', path: '/exceptions', icon: AlertTriangle, badge: openExceptionsCount },
    { label: 'Finance Assistant', path: '/assistant', icon: Bot, highlight: true },
    { label: 'Audit Trail', path: '/audit', icon: ShieldCheck },
    { label: 'Settings', path: '/settings', icon: Settings },
  ];

  return (
    <aside className="w-64 bg-[#0B0F19] border-r border-[#1E293B] flex flex-col shrink-0 min-h-screen">
      {/* Brand Header */}
      <div className="h-16 flex items-center px-6 border-b border-[#1E293B] gap-3">
        <div className="w-8 h-8 rounded-lg bg-teal-500/10 border border-teal-500/30 flex items-center justify-center text-teal-400">
          <Scale className="w-5 h-5" />
        </div>
        <div>
          <div className="flex items-center gap-2">
            <span className="font-bold tracking-tight text-slate-100 text-base">LedgerSync</span>
            <span className="text-[10px] uppercase font-mono px-1.5 py-0.5 rounded bg-teal-950/80 border border-teal-800 text-teal-300 font-semibold">2.0</span>
          </div>
          {/* <span className="text-[11px] text-slate-500 block -mt-0.5 font-mono">Track 4: AI Controller</span> */}
        </div>
      </div>

      {/* Navigation */}
      <div className="px-3 py-4 flex-1 space-y-1">
        <div className="px-3 mb-2 text-[10px] font-semibold text-slate-500 uppercase tracking-wider font-mono">
          Core Platform
        </div>
        {navItems.map((item) => (
          <NavLink
            key={item.path}
            to={item.path}
            className={({ isActive }) =>
              `flex items-center justify-between px-3 py-2.5 rounded-lg text-sm font-medium transition-all group ${
                isActive
                  ? 'bg-teal-500/10 text-teal-300 border border-teal-500/20'
                  : 'text-slate-400 hover:text-slate-200 hover:bg-[#111827]'
              }`
            }
          >
            <div className="flex items-center gap-3">
              <item.icon className="w-4 h-4 shrink-0 transition-colors group-hover:text-teal-400" />
              <span>{item.label}</span>
            </div>
            {item.badge !== undefined && item.badge > 0 ? (
              <span className="px-2 py-0.5 text-xs font-mono font-semibold rounded-full bg-rose-950/80 border border-rose-800 text-rose-300">
                {item.badge}
              </span>
            ) : null}
            {item.highlight && (
              <span className="w-1.5 h-1.5 rounded-full bg-teal-400 animate-pulse" />
            )}
          </NavLink>
        ))}
      </div>

      {/* System Status Pill */}
      <div className="p-4 border-t border-[#1E293B]">
        <div className="bg-[#111827] border border-[#1E293B] rounded-lg p-3 text-xs">
          <div className="flex items-center justify-between text-slate-400 mb-1.5">
            <span className="text-[11px] uppercase font-mono font-medium">Reconciliation Mode</span>
            <span className="w-2 h-2 rounded-full bg-emerald-400" />
          </div>
          <div className="text-slate-200 font-mono text-[11px] truncate">
            Deterministic 3-Way Engine
          </div>
          <div className="text-slate-500 font-mono text-[10px] mt-1">
            Minor Unit Invariants: Paise
          </div>
        </div>
      </div>
    </aside>
  );
};
