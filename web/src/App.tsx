import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { AuthProvider, useAuth } from './auth';
import type { Role } from './api/types';
import { navigate, useHashRoute } from './router';
import type { RouteState } from './router';
import { AppShell, ForbiddenPage, NotFoundPage, routeTitle } from './components/Layout';
import { ToastHost } from './components/Toast';
import { ErrorBoundary } from './components/ErrorBoundary';
import { MAIN_ONLY } from './config';
import { LoginPage } from './pages/Login';
import { DashboardPage } from './pages/Dashboard';
import { CasesPage } from './pages/Cases';
import { ApiCasesPage } from './pages/ApiCases';
import { EnvironmentsPage } from './pages/Environments';
import { RequirementsPage } from './pages/Requirements';
import { AiPage } from './pages/Ai';
import { PlansPage } from './pages/Plans';
import { JobsPage } from './pages/Jobs';
import { PlanDetailPage } from './pages/PlanDetail';
import { RunDetailPage } from './pages/RunDetail';
import { TasksPage } from './pages/Tasks';
import { TaskDetailPage } from './pages/TaskDetail';
import { DevicesPage } from './pages/Devices';
import { SettingsPage } from './pages/Settings';

const ALL_ROLES: Role[] = ['ADMIN', 'TESTER', 'BUSINESS', 'OPS'];
const TESTERS: Role[] = ['ADMIN', 'TESTER'];
const CROSS: Role[] = ['ADMIN', 'TESTER', 'BUSINESS'];

const ROUTE_ROLES: Record<string, Role[]> = {
  dashboard: ALL_ROLES,
  devices: ALL_ROLES,
  tasks: ALL_ROLES,
  cases: CROSS,
  'api-cases': TESTERS,
  environments: TESTERS,
  requirements: CROSS,
  ai: CROSS,
  plans: TESTERS,
  jobs: TESTERS,
  runs: TESTERS,
  settings: ['ADMIN'],
};

function renderPage(route: RouteState): ReactNode {
  const [first, second] = route.segments;
  switch (first) {
    case undefined:
    case 'dashboard':
      return <DashboardPage />;
    case 'devices':
      return <DevicesPage />;
    case 'tasks':
      return second ? <TaskDetailPage /> : <TasksPage />;
    case 'requirements':
      return second ? <NotFoundPage routePath={route.path} /> : <RequirementsPage />;
    case 'cases':
      return second ? <NotFoundPage routePath={route.path} /> : <CasesPage />;
    case 'api-cases':
      return second ? <NotFoundPage routePath={route.path} /> : <ApiCasesPage />;
    case 'environments':
      return second ? <NotFoundPage routePath={route.path} /> : <EnvironmentsPage />;
    case 'ai':
      return second ? <NotFoundPage routePath={route.path} /> : <AiPage />;
    case 'plans':
      return second ? <PlanDetailPage /> : <PlansPage />;
    case 'jobs':
      return second ? <NotFoundPage routePath={route.path} /> : <JobsPage />;
    case 'runs':
      return second ? <RunDetailPage /> : <NotFoundPage routePath={route.path} />;
    case 'settings':
      return second ? <NotFoundPage routePath={route.path} /> : <SettingsPage />;
    default:
      return <NotFoundPage routePath={route.path} />;
  }
}

function AppRoutes() {
  const route = useHashRoute();
  const { authenticated, user } = useAuth();
  const [refreshKey, setRefreshKey] = useState(0);
  const first = route.segments[0] || 'dashboard';
  const isLogin = first === 'login';
  const knownRoute = ['dashboard', 'devices', 'tasks', 'requirements', 'cases', 'api-cases', 'environments', 'ai', 'plans', 'jobs', 'runs', 'settings', 'login'];
  const pageExists = !route.segments.length || knownRoute.includes(first);

  useEffect(() => {
    if (!authenticated && !isLogin) navigate('/login', { replace: true });
    if (authenticated && isLogin) navigate(MAIN_ONLY ? '/cases' : '/dashboard', { replace: true });
  }, [authenticated, isLogin]);

  useEffect(() => {
    if (!MAIN_ONLY) return;
    if (!route.segments.length || ['dashboard', 'devices', 'tasks'].includes(first)) {
      navigate('/cases', { replace: true });
    }
  }, [route.path, route.segments.length, first]);

  useEffect(() => {
    document.title = `${routeTitle(route)} · TestHub`;
  }, [route]);

  if (!authenticated || isLogin) {
    return (
      <>
        <LoginPage />
        <ToastHost />
      </>
    );
  }

  const allowedRoles = ROUTE_ROLES[first];
  const allowed = !allowedRoles || (user ? allowedRoles.includes(user.role) : false);

  let content: ReactNode;
  let shellTitle = routeTitle(route);
  if (!pageExists) {
    content = <NotFoundPage routePath={route.path} />;
    shellTitle = '页面不存在';
  } else if (!allowed) {
    content = <ForbiddenPage />;
    shellTitle = '无权访问';
  } else {
    content = renderPage(route);
  }

  return (
    <>
      <AppShell title={shellTitle} onRefresh={() => setRefreshKey((prev) => prev + 1)}>
        <div className="route-viewport" key={`${route.path}-${refreshKey}`}>
          {content}
        </div>
      </AppShell>
      <ToastHost />
    </>
  );
}

export default function App() {
  return (
    <ErrorBoundary>
      <AuthProvider>
        <AppRoutes />
      </AuthProvider>
    </ErrorBoundary>
  );
}
