import React from 'react';
import { LucideIcon } from 'lucide-react';

interface Props {
  title: string;
  value: string | number;
  subtitle?: string;
  icon: LucideIcon;
  badge?: string;
  badgeColor?: 'emerald' | 'rose' | 'amber' | 'teal' | 'blue';
}

export const MetricCard: React.FC<Props> = ({
  title,
  value,
  subtitle,
  icon: Icon,
  badge,
  badgeColor = 'teal',
}) => {
  const badgeStyles = {
    emerald: 'bg-emerald-950/60 text-emerald-400 border-emerald-800/40',
    rose: 'bg-rose-950/60 text-rose-400 border-rose-800/40',
    amber: 'bg-amber-950/60 text-amber-400 border-amber-800/40',
    teal: 'bg-teal-950/60 text-teal-400 border-teal-800/40',
    blue: 'bg-sky-950/60 text-sky-400 border-sky-800/40',
  }[badgeColor];

  return (
    <div className="bg-[#111827] border border-[#1E293B] rounded-xl p-5 hover:border-[#334155] transition-colors relative overflow-hidden group">
      <div className="flex items-center justify-between mb-3">
        <span className="text-xs font-medium text-slate-400 uppercase tracking-wider">{title}</span>
        <div className="p-2 rounded-lg bg-[#0F172A] border border-[#1E293B] text-teal-400 group-hover:text-teal-300 transition-colors">
          <Icon className="w-4 h-4" />
        </div>
      </div>

      <div className="flex flex-col items-start gap-2">
        <div className="max-w-full break-words text-xl sm:text-2xl font-bold font-mono text-slate-100">{value}</div>
        {badge && (
          <span className={`text-xs px-2 py-0.5 rounded border font-mono ${badgeStyles}`}>
            {badge}
          </span>
        )}
      </div>

      {subtitle && <p className="text-xs text-slate-400 mt-2">{subtitle}</p>}
    </div>
  );
};
