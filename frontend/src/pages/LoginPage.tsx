import React, { FormEvent, useState } from 'react';
import { LockKeyhole } from 'lucide-react';
import { api } from '../services/api';
import { AuthSession } from '../types';

interface Props {
  onLogin: (session: AuthSession) => void;
  connectionError: string | null;
}

export const LoginPage: React.FC<Props> = ({ onLogin, connectionError }) => {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const session = await api.login(username, password);
      onLogin({
        authenticated: true,
        username: session.username,
        role: session.role,
      });
    } catch (loginError) {
      setError(loginError instanceof Error ? loginError.message : 'Login failed.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <main className="min-h-screen bg-[#0B0F19] text-[#F8FAFC] flex items-center justify-center px-6">
      <form onSubmit={submit} className="w-full max-w-sm rounded-2xl border border-[#1E293B] bg-[#111827] p-8 shadow-2xl">
        <div className="mb-6 flex items-center gap-3">
          <div className="rounded-xl bg-teal-500/10 p-3 text-teal-300">
            <LockKeyhole className="h-5 w-5" />
          </div>
          <div>
            <h1 className="text-xl font-semibold">LedgerSync sign in</h1>
            <p className="mt-1 text-xs text-slate-400">Use your configured finance account.</p>
          </div>
        </div>

        {(error || connectionError) && (
          <p role="alert" className="mb-4 rounded-lg border border-rose-900 bg-rose-950/50 p-3 text-sm text-rose-200">
            {error || connectionError}
          </p>
        )}

        <label className="mb-4 block text-sm text-slate-300">
          Username
          <input
            autoComplete="username"
            required
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            className="mt-2 w-full rounded-lg border border-slate-700 bg-[#0B0F19] px-3 py-2.5 text-white outline-none focus:border-teal-500"
          />
        </label>

        <label className="mb-6 block text-sm text-slate-300">
          Password
          <input
            autoComplete="current-password"
            required
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            className="mt-2 w-full rounded-lg border border-slate-700 bg-[#0B0F19] px-3 py-2.5 text-white outline-none focus:border-teal-500"
          />
        </label>

        <button
          type="submit"
          disabled={submitting}
          className="w-full rounded-lg bg-teal-600 px-4 py-2.5 font-semibold text-white transition-colors hover:bg-teal-500 disabled:cursor-not-allowed disabled:opacity-60"
        >
          {submitting ? 'Signing in...' : 'Sign in'}
        </button>
      </form>
    </main>
  );
};
