import React from 'react';
import { DiscrepancySeverity } from '../types';

interface Props {
  severity: DiscrepancySeverity | string;
}

export const SeverityBadge: React.FC<Props> = ({ severity }) => {
  let style = 'bg-slate-800/80 text-slate-300 border-slate-700';

  switch (severity?.toUpperCase()) {
    case 'CRITICAL':
      style = 'bg-rose-950/60 text-rose-200 border-rose-700 font-semibold';
      break;
    case 'HIGH':
      style = 'bg-orange-950/50 text-orange-200 border-orange-700';
      break;
    case 'MEDIUM':
      style = 'bg-amber-950/40 text-amber-200 border-amber-800';
      break;
    case 'LOW':
      style = 'bg-slate-800 text-slate-300 border-slate-700';
      break;
  }

  return (
    <span className={`inline-block px-2 py-0.5 text-[11px] font-mono uppercase tracking-wider rounded border ${style}`}>
      {severity}
    </span>
  );
};
