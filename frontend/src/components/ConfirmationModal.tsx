import React, { useEffect, useState } from 'react';
import { AlertCircle, CheckCircle, X } from 'lucide-react';
import { ResolutionActionType } from '../types';
import { formatMinorUnits, parseRupeesToMinorUnits } from '../utils/currency';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: (notes: string, adjustmentMinor?: number) => void;
  actionType: ResolutionActionType;
  title: string;
  description: string;
  defaultAdjustmentMinor?: number;
  loading?: boolean;
}

export const ConfirmationModal: React.FC<Props> = ({
  isOpen,
  onClose,
  onConfirm,
  actionType,
  title,
  description,
  defaultAdjustmentMinor = 0,
  loading = false,
}) => {
  const [notes, setNotes] = useState('');
  const [adjustment, setAdjustment] = useState(
    formatMinorUnits(defaultAdjustmentMinor)
  );
  const [adjustmentError, setAdjustmentError] = useState<string | null>(null);

  useEffect(() => {
    if (isOpen) {
      setNotes('');
      setAdjustment(formatMinorUnits(defaultAdjustmentMinor));
      setAdjustmentError(null);
    }
  }, [isOpen, defaultAdjustmentMinor, actionType]);

  if (!isOpen) return null;

  const isReject = actionType === 'REJECT_INVESTIGATION';
  const isApprove = actionType === 'APPROVE_ADJUSTMENT' || actionType === 'FORCE_MATCH';
  const handleConfirm = () => {
    try {
      const adjustmentMinor = actionType === 'APPROVE_ADJUSTMENT'
        ? parseRupeesToMinorUnits(adjustment)
        : undefined;
      setAdjustmentError(null);
      onConfirm(notes, adjustmentMinor);
    } catch (error) {
      setAdjustmentError(error instanceof Error ? error.message : 'Enter a valid amount.');
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm animate-in fade-in duration-150">
      <div className="bg-[#111827] border border-[#1E293B] rounded-xl max-w-lg w-full p-6 shadow-2xl relative">
        <button
          onClick={onClose}
          className="absolute top-4 right-4 text-slate-400 hover:text-slate-200 transition-colors"
        >
          <X className="w-5 h-5" />
        </button>

        <div className="flex items-center gap-3 mb-4">
          <div
            className={`p-2.5 rounded-lg border ${
              isReject
                ? 'bg-rose-950/50 text-rose-400 border-rose-800'
                : 'bg-teal-950/50 text-teal-400 border-teal-800'
            }`}
          >
            {isReject ? <AlertCircle className="w-5 h-5" /> : <CheckCircle className="w-5 h-5" />}
          </div>
          <div>
            <h3 className="text-lg font-semibold text-slate-100">{title}</h3>
            <span className="text-xs font-mono uppercase text-slate-400">Action: {actionType}</span>
          </div>
        </div>

        <p className="text-sm text-slate-300 mb-5 leading-relaxed">{description}</p>

        <div className="space-y-4 mb-6">
          {actionType === 'APPROVE_ADJUSTMENT' && (
            <div>
              <label className="block text-xs font-medium text-slate-400 uppercase tracking-wider mb-1.5">
                Financial Adjustment (INR)
              </label>
              <div className="relative">
                <span className="absolute left-3 top-2.5 text-slate-500 font-mono text-sm">₹</span>
                <input
                  type="text"
                  inputMode="decimal"
                  value={adjustment}
                  onChange={(e) => {
                    setAdjustment(e.target.value);
                    setAdjustmentError(null);
                  }}
                  className="w-full pl-8 pr-4 py-2 bg-[#0F172A] border border-[#1E293B] rounded-lg text-slate-100 font-mono text-sm focus:outline-none focus:border-teal-500 transition-colors"
                />
              </div>
              {adjustmentError && <p className="mt-1 text-xs text-rose-400">{adjustmentError}</p>}
            </div>
          )}

          <div>
            <label className="block text-xs font-medium text-slate-400 uppercase tracking-wider mb-1.5">
              Controller Decision Notes & Audit Rationale
            </label>
            <textarea
              rows={3}
              placeholder="Provide justification for immutable audit trail (e.g., Verified against Razorpay nodal clearing report)..."
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              className="w-full px-3 py-2 bg-[#0F172A] border border-[#1E293B] rounded-lg text-slate-100 text-sm focus:outline-none focus:border-teal-500 transition-colors"
            />
          </div>
        </div>

        <div className="flex items-center justify-end gap-3 pt-4 border-t border-[#1E293B]">
          <button
            onClick={onClose}
            disabled={loading}
            className="px-4 py-2 text-sm font-medium text-slate-300 hover:text-white bg-[#0F172A] border border-[#1E293B] rounded-lg hover:border-slate-600 transition-colors"
          >
            Cancel
          </button>
          <button
            onClick={handleConfirm}
            disabled={loading}
            className={`px-4 py-2 text-sm font-semibold rounded-lg shadow-lg flex items-center gap-2 transition-colors ${
              isReject
                ? 'bg-rose-600 hover:bg-rose-500 text-white'
                : 'bg-teal-600 hover:bg-teal-500 text-white'
            }`}
          >
            {loading && <span className="animate-spin w-4 h-4 border-2 border-white/30 border-t-white rounded-full" />}
            Confirm & Execute Action
          </button>
        </div>
      </div>
    </div>
  );
};
