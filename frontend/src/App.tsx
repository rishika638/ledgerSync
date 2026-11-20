import React, { useEffect, useState } from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { Layout } from './components/Layout';
import { AssistantPage } from './pages/AssistantPage';
import { AuditPage } from './pages/AuditPage';
import { DashboardPage } from './pages/DashboardPage';
import { ExceptionsPage } from './pages/ExceptionsPage';
import { ImportsPage } from './pages/ImportsPage';
import { InvestigationWorkspacePage } from './pages/InvestigationWorkspacePage';
import { LoginPage } from './pages/LoginPage';
import { ReconciliationPage } from './pages/ReconciliationPage';
import { RunDetailsPage } from './pages/RunDetailsPage';
import { SettingsPage } from './pages/SettingsPage';
import { TransactionsPage } from './pages/TransactionsPage';
import { api } from './services/api';
import { AuthSession } from './types';

interface AuthenticatedRoutesProps {
  session: AuthSession;
  onLogout: () => Promise<void>;
}

const AuthenticatedRoutes: React.FC<AuthenticatedRoutesProps> = ({ session, onLogout }) => (
  <Layout username={session.username || ''} role={session.role || 'OPERATOR'} onLogout={onLogout}>
    <Routes>
      <Route path="/" element={<Navigate to="/dashboard" replace />} />
      <Route path="/dashboard" element={<DashboardPage />} />
      <Route path="/imports" element={<ImportsPage />} />
      <Route path="/reconciliation" element={<ReconciliationPage />} />
      <Route path="/reconciliation/:runId" element={<RunDetailsPage />} />
      <Route path="/transactions" element={<TransactionsPage />} />
      <Route path="/exceptions" element={<ExceptionsPage />} />
      <Route path="/exceptions/:id" element={<InvestigationWorkspacePage />} />
      <Route path="/assistant" element={<AssistantPage />} />
      <Route path="/audit" element={<AuditPage />} />
      <Route path="/settings" element={<SettingsPage />} />
      <Route path="/login" element={<Navigate to="/dashboard" replace />} />
      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  </Layout>
);

export function App() {
  const [session, setSession] = useState<AuthSession | null>(null);
  const [checkingSession, setCheckingSession] = useState(true);
  const [connectionError, setConnectionError] = useState<string | null>(null);

  useEffect(() => {
    api.getSession()
      .then((currentSession) => {
        setSession(currentSession.authenticated ? currentSession : null);
      })
      .catch((error: unknown) => {
        setSession(null);
        setConnectionError(error instanceof Error ? error.message : 'Unable to reach the LedgerSync API.');
      })
      .finally(() => setCheckingSession(false));
  }, []);

  const logout = async () => {
    await api.logout();
    setSession(null);
  };

  if (checkingSession) {
    return (
      <div className="min-h-screen bg-[#0B0F19] text-slate-300 flex items-center justify-center">
        Checking session...
      </div>
    );
  }

  return (
    <BrowserRouter>
      {session ? (
        <AuthenticatedRoutes session={session} onLogout={logout} />
      ) : (
        <Routes>
          <Route
            path="/login"
            element={<LoginPage onLogin={setSession} connectionError={connectionError} />}
          />
          <Route path="*" element={<Navigate to="/login" replace />} />
        </Routes>
      )}
    </BrowserRouter>
  );
}

export default App;
