import { request } from './client';

export interface DashboardSummary {
  requirements: number;
  cases: number;
  approvedCases: number;
  plans: number;
  apiCases: number;
  runs: number;
  success: number;
  failed: number;
  timeout: number;
  cancelled: number;
  totalItems: number;
  passRate: number;
  recentRuns: Array<{
    runId: string;
    planId: string;
    status: string;
    total: number;
    success: number;
    failed: number;
    timeout: number;
    startedAt?: string;
    finishedAt?: string;
  }>;
  failures: Array<{ reason: string; count: number }>;
}

export const dashboardApi = {
  summary: () => request<DashboardSummary>('core', '/api/v1/dashboard'),
};
