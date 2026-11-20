import React, { useState } from 'react';
import {
  Bot,
  Sparkles,
  Send,
  HelpCircle,
  Database,
  ShieldCheck,
  CheckCircle2,
  RefreshCw,
  ExternalLink
} from 'lucide-react';
import { api } from '../services/api';
import { AssistantResponse } from '../types';

export const AssistantPage: React.FC = () => {
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(false);
  const [conversation, setConversation] = useState<
    Array<{
      sender: 'user' | 'assistant';
      text: string;
      citations?: string[];
      metrics?: Record<string, any>;
    }>
  >([
    {
      sender: 'assistant',
      text: "Hello! I am your AI Finance Controller Assistant. I provide real-time, evidence-grounded answers about your reconciliation runs, open financial exposure, settlement discrepancies, and exceptions directly from the verified PostgreSQL ledger. What would you like to analyze?",
      citations: [
        'PostgreSQL Database Truth Grounding Active',
        'Model-Generated SQL Execution: Strictly Forbidden',
      ],
    },
  ]);

  const [suggestedQuestions, setSuggestedQuestions] = useState<string[]>([
    'What is our total unmatched financial exposure right now?',
    'How many Razorpay settlements are missing corresponding bank statement credits?',
    'What was the match rate and duration in the latest reconciliation run?',
    'Which exception categories represent the highest financial risk?',
  ]);

  const handleAsk = async (questionText: string) => {
    if (!questionText.trim() || loading) return;

    const userText = questionText;
    setQuery('');
    setConversation((prev) => [...prev, { sender: 'user', text: userText }]);
    setLoading(true);

    try {
      const res: AssistantResponse = await api.askAssistant(userText);
      setConversation((prev) => [
        ...prev,
        {
          sender: 'assistant',
          text: res.answer,
          citations: res.evidenceCitations,
          metrics: res.groundTruthMetrics,
        },
      ]);
      if (res.suggestedQuestions && res.suggestedQuestions.length > 0) {
        setSuggestedQuestions(res.suggestedQuestions);
      }
    } catch (err: any) {
      setConversation((prev) => [
        ...prev,
        {
          sender: 'assistant',
          text: `Error retrieving financial evidence: ${err.message}`,
        },
      ]);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="h-[calc(100vh-8rem)] flex flex-col space-y-4 animate-in fade-in duration-200">
      {/* Header */}
      <div className="flex items-center justify-between border-b border-[#1E293B] pb-4 shrink-0">
        <div className="flex items-center gap-3">
          <div className="p-2.5 rounded-lg bg-teal-500/10 text-teal-400 border border-teal-500/20">
            <Bot className="w-5 h-5" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-xl font-bold tracking-tight text-slate-100">AI Finance Controller Assistant</h1>
              <span className="text-xs font-mono px-2 py-0.5 rounded bg-teal-950/80 border border-teal-800 text-teal-300 font-semibold">
                Read-Only Safe
              </span>
            </div>
            <p className="text-xs text-slate-400 mt-0.5">
              Evidence-linked financial answers grounded in actual database metrics.
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2 text-xs font-mono text-slate-500 bg-[#111827] px-3 py-1.5 rounded-lg border border-[#1E293B]">
          <ShieldCheck className="w-4 h-4 text-teal-400" />
          <span>Zero Arbitrary SQL</span>
        </div>
      </div>

      {/* Suggested Questions Pills */}
      <div className="flex items-center gap-2 overflow-x-auto py-1 shrink-0 scrollbar-none">
        <span className="text-xs font-mono text-slate-500 uppercase shrink-0">Quick Queries:</span>
        {suggestedQuestions.map((q, idx) => (
          <button
            key={idx}
            onClick={() => handleAsk(q)}
            disabled={loading}
            className="text-xs font-mono px-3 py-1.5 rounded-full bg-[#111827] hover:bg-[#1E293B] border border-[#1E293B] hover:border-teal-500/50 text-slate-300 hover:text-teal-300 whitespace-nowrap transition-colors"
          >
            {q}
          </button>
        ))}
      </div>

      {/* Chat Messages Feed */}
      <div className="flex-1 bg-[#111827] border border-[#1E293B] rounded-xl p-5 overflow-y-auto space-y-4">
        {conversation.map((msg, idx) => (
          <div
            key={idx}
            className={`flex ${msg.sender === 'user' ? 'justify-end' : 'justify-start'}`}
          >
            <div
              className={`max-w-2xl rounded-xl p-4 text-xs ${
                msg.sender === 'user'
                  ? 'bg-teal-600 text-white font-medium rounded-br-none'
                  : 'bg-[#0F172A] border border-[#1E293B] text-slate-200 rounded-bl-none space-y-3'
              }`}
            >
              <div className="leading-relaxed whitespace-pre-wrap">{msg.text}</div>

              {/* Citations & Evidence Pill */}
              {msg.citations && msg.citations.length > 0 && (
                <div className="pt-2 border-t border-[#1E293B] space-y-1 font-mono text-[11px] text-teal-400">
                  <span className="text-[10px] uppercase font-semibold text-slate-500 block">
                    Verified Citations & Grounding:
                  </span>
                  {msg.citations.map((c, i) => (
                    <div key={i} className="flex items-start gap-1.5 text-slate-300">
                      <span className="text-teal-400">#</span>
                      <span>{c}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        ))}

        {loading && (
          <div className="flex justify-start">
            <div className="bg-[#0F172A] border border-[#1E293B] rounded-xl p-4 text-xs font-mono text-teal-400 flex items-center gap-2">
              <RefreshCw className="w-4 h-4 animate-spin" />
              <span>Querying database invariants and synthesizing evidence...</span>
            </div>
          </div>
        )}
      </div>

      {/* Input Bar */}
      <form
        onSubmit={(e) => {
          e.preventDefault();
          handleAsk(query);
        }}
        className="relative shrink-0"
      >
        <input
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Ask a financial question (e.g. 'What is our unmatched exposure by exception type?')..."
          className="w-full pl-4 pr-12 py-3 bg-[#111827] border border-[#1E293B] rounded-xl text-xs text-slate-100 placeholder-slate-500 focus:outline-none focus:border-teal-500 transition-colors font-mono"
        />
        <button
          type="submit"
          disabled={!query.trim() || loading}
          className="absolute right-2 top-2 p-1.5 rounded-lg bg-teal-600 hover:bg-teal-500 disabled:opacity-40 text-white transition-colors"
        >
          <Send className="w-4 h-4" />
        </button>
      </form>
    </div>
  );
};
