import React from 'react';

interface Props {
  status: string;
  size?: 'sm' | 'md';
}

export const StatusBadge: React.FC<Props> = ({ status, size = 'md' }) => {
  let bg = 'bg-slate-800/80 text-slate-300 border-slate-700';
  let dot = 'bg-slate-400';

  const s = status.toUpperCase();

  if (s === 'MATCHED' || s === 'RESOLVED' || s === 'COMPLETED' || s === 'APPROVED') {
    bg = 'bg-emerald-950/40 text-emerald-300 border-emerald-800/50';
    dot = 'bg-emerald-400';
  } else if (s === 'AWAITING_APPROVAL' || s === 'INVESTIGATED' || s === 'PROCESSING') {
    bg = 'bg-amber-950/40 text-amber-300 border-amber-800/50';
    dot = 'bg-amber-400';
  } else if (s === 'OPEN' || s === 'DISCREPANCY' || s === 'FAILED' || s === 'REJECTED') {
    bg = 'bg-rose-950/40 text-rose-300 border-rose-800/50';
    dot = 'bg-rose-400';
  } else if (s === 'PARTIALLY_MATCHED' || s === 'INVESTIGATING') {
    bg = 'bg-sky-950/40 text-sky-300 border-sky-800/50';
    dot = 'bg-sky-400';
  }

  const padding = size === 'sm' ? 'px-2 py-0.5 text-xs' : 'px-2.5 py-1 text-xs';

  return (
    <span className={`inline-flex items-center gap-1.5 font-medium rounded-full border ${bg} ${padding}`}>
      <span className={`w-1.5 h-1.5 rounded-full ${dot} shrink-0`} />
      <span className="tracking-wide uppercase font-mono">{status.replace(/_/g, ' ')}</span>
    </span>
  );
};
