import React, { useState, useEffect } from 'react';
import { Sidebar } from './Sidebar';
import { Navbar } from './Navbar';
import { api } from '../services/api';

interface Props {
  children: React.ReactNode;
  username: string;
  role: 'OPERATOR' | 'ADMIN';
  onLogout: () => Promise<void>;
}

export const Layout: React.FC<Props> = ({ children, username, role, onLogout }) => {
  const [openExceptions, setOpenExceptions] = useState(0);
  const [geminiConnected, setGeminiConnected] = useState(true);

  const fetchGlobalStats = async () => {
    try {
      const metrics = await api.getDashboardMetrics();
      setOpenExceptions(metrics.unmatchedRecords);
      setGeminiConnected(metrics.geminiStatus?.configured ?? true);
    } catch (_) {}
  };

  useEffect(() => {
    fetchGlobalStats();
  }, []);

  return (
    <div className="flex min-h-screen bg-[#0B0F19] text-[#F8FAFC]">
      <Sidebar openExceptionsCount={openExceptions} />
      <div className="flex-1 flex flex-col min-w-0">
        <Navbar
          onReconciliationTriggered={fetchGlobalStats}
          geminiConnected={geminiConnected}
          username={username}
          role={role}
          onLogout={onLogout}
        />
        <main className="flex-1 p-8 overflow-y-auto max-w-7xl w-full mx-auto">
          {children}
        </main>
      </div>
    </div>
  );
};
