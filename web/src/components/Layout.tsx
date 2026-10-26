import { useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { useAuth } from '../auth';
import { MAIN_ONLY } from '../config';
import { ROLE_META, metaOf } from '../constants';
import type { Role } from '../api/types';
import { cx, formatDateTime, userDisplayName } from '../utils';
import { navigate, type RouteState, useHashRoute } from '../router';
import { BrandMark, Icon } from './icons';
import type { IconName } from './icons';
import { Avatar, IconButton, Pill } from './ui';

interface NavItem {
  path: string;
  label: string;
  icon: IconName;
  roles: Role[];
}

interface NavGroup {
  title: string;
  items: NavItem[];
}

const NAV_GROUPS: NavGroup[] = [
  {
    title: '工作台',
    items: [
      { path: '/dashboard', label: '总览', icon: 'dashboard', roles: ['ADMIN', 'TESTER', 'BUSINESS', 'OPS'] },
      { path: '/devices', label: '设备池', icon: 'devices', roles: ['ADMIN', 'TESTER', 'BUSINESS', 'OPS'] },
      { path: '/tasks', label: '执行任务', icon: 'tasks', roles: ['ADMIN', 'TESTER', 'BUSINESS', 'OPS'] },
    ],
  },
  {
    title: '测试资产',
    items: [
      { path: '/requirements', label: '需求管理', icon: 'cases', roles: ['ADMIN', 'TESTER', 'BUSINESS'] },
      { path: '/cases', label: '用例管理', icon: 'cases', roles: ['ADMIN', 'TESTER', 'BUSINESS'] },
      { path: '/api-cases', label: '接口用例', icon: 'api', roles: ['ADMIN', 'TESTER'] },
      { path: '/environments', label: '环境管理', icon: 'server', roles: ['ADMIN', 'TESTER'] },
      { path: '/ui-elements', label: '元素库', icon: 'cases', roles: ['ADMIN', 'TESTER'] },
      { path: '/plans', label: '测试计划', icon: 'plans', roles: ['ADMIN', 'TESTER'] },
      { path: '/jobs', label: '定时任务', icon: 'clock', roles: ['ADMIN', 'TESTER'] },
      { path: '/reports', label: '测试报告', icon: 'dashboard', roles: ['ADMIN', 'TESTER', 'BUSINESS'] },
      { path: '/ai', label: 'AI 分析', icon: 'ai', roles: ['ADMIN', 'TESTER', 'BUSINESS'] },
    ],
  },
  {
    title: '系统',
    items: [{ path: '/settings', label: '系统设置', icon: 'settings', roles: ['ADMIN'] }],
  },
];

export function isPathActive(current: string, target: string): boolean {
  return current === target || current.startsWith(`${target}/`);
}

export function routeTitle(route: RouteState): string {
  const [first] = route.segments;
  switch (first) {
    case undefined:
    case 'dashboard':
      return '总览';
    case 'devices':
      return '设备池';
    case 'tasks':
      return route.segments[1] ? '任务详情' : '执行任务';
    case 'requirements':
      return '需求管理';
    case 'cases':
      return '用例管理';
    case 'api-cases':
      return '接口用例';
    case 'environments':
      return '环境管理';
    case 'ui-elements':
      return '元素库';
    case 'ai':
      return 'AI 分析';
    case 'plans':
      return route.segments[1] ? '测试计划详情' : '测试计划';
    case 'jobs':
      return '定时任务';
    case 'reports':
      return '测试报告';
    case 'runs':
      return '运行报告';
    case 'settings':
      return '系统设置';
    case 'login':
      return '登录';
    default:
      return '页面不存在';
  }
}

function Sidebar({ currentPath }: { currentPath: string }) {
  const { user } = useAuth();
  const role = user?.role;
  const groups = useMemo(
    () =>
      NAV_GROUPS.map((group) => ({
        ...group,
        items: group.items.filter((item) => {
          if (role ? !item.roles.includes(role) : true) return false;
          return !(MAIN_ONLY && ['/dashboard', '/devices', '/tasks'].includes(item.path));
        }),
      })).filter((group) => group.items.length > 0),
    [role],
  );

  return (
    <aside className="sidebar">
      <button type="button" className="brand" onClick={() => navigate('/dashboard')}>
        <BrandMark size={38} />
        <span className="brand-text">
          <span className="brand-name">TestHub</span>
          <span className="brand-sub">一站式测试平台</span>
        </span>
      </button>

      <nav className="nav" aria-label="主导航">
        {groups.map((group) => (
          <div className="nav-group" key={group.title}>
            <div className="nav-group-title">{group.title}</div>
            {group.items.map((item) => {
              const active = isPathActive(currentPath, item.path);
              return (
                <button
                  type="button"
                  key={item.path}
                  className={cx('nav-item', active && 'is-active')}
                  aria-current={active ? 'page' : undefined}
                  onClick={() => navigate(item.path)}
                >
                  <span className="nav-icon">
                    <Icon name={item.icon} size={18} />
                  </span>
                  <span className="nav-label">{item.label}</span>
                </button>
              );
            })}
          </div>
        ))}
      </nav>

      <div className="sidebar-foot">
        <div className="sidebar-env">
          <span className="env-dot" />
          TestHub V1
        </div>
        <div className="sidebar-copy">本地开发环境</div>
      </div>
    </aside>
  );
}

function CurrentClock() {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), 1000);
    return () => window.clearInterval(timer);
  }, []);
  return (
    <span className="topbar-clock" title="当前时间">
      <Icon name="clock" size={14} />
      <span>{formatDateTime(now)}</span>
    </span>
  );
}

function Topbar({
  title,
  onRefresh,
  onLogout,
}: {
  title: string;
  onRefresh: () => void;
  onLogout: () => void;
}) {
  const { user } = useAuth();
  const roleMeta = metaOf(ROLE_META, user?.role);
  const [spinning, setSpinning] = useState(false);

  const refresh = () => {
    setSpinning(true);
    onRefresh();
    window.setTimeout(() => setSpinning(false), 650);
  };

  return (
    <header className="topbar">
      <div className="topbar-left">
        <h2 className="topbar-title">{title}</h2>
      </div>
      <div className="topbar-right">
        <CurrentClock />
        <button type="button" className="topbar-refresh" onClick={refresh} title="刷新当前页面">
          <Icon name="refresh" size={16} className={spinning ? 'spin' : undefined} />
          <span className="topbar-refresh-label">刷新</span>
        </button>
        <div className="topbar-divider" />
        <div className="topbar-user">
          <Avatar name={userDisplayName(user) || 'U'} size={32} />
          <div className="topbar-user-meta">
            <span className="topbar-user-name">{userDisplayName(user) || '未登录'}</span>
            <Pill tone={roleMeta.tone}>{roleMeta.label}</Pill>
          </div>
        </div>
        <button type="button" className="topbar-logout" onClick={onLogout} title="退出登录">
          <Icon name="logout" size={16} />
          <span>退出</span>
        </button>
      </div>
    </header>
  );
}

export function AppShell({
  title,
  children,
  onRefresh,
}: {
  title: string;
  children: ReactNode;
  onRefresh: () => void;
}) {
  const { logout } = useAuth();
  const route = useHashRoute();
  return (
    <div className="app-shell">
      <Sidebar currentPath={route.path} />
      <div className="app-main">
        <Topbar title={title} onRefresh={onRefresh} onLogout={logout} />
        <main className="app-content">{children}</main>
      </div>
    </div>
  );
}

export function ServiceUnavailable({ title, detail }: { title: string; detail?: string }) {
  return (
    <div className="service-unavailable">
      <span className="service-unavailable-icon">
        <Icon name="server" size={22} />
      </span>
      <div>
        <div className="service-unavailable-title">{title}</div>
        <div className="service-unavailable-text">
          {detail || '请确认后端服务已启动，或在登录页 / 系统设置中检查服务地址。'}
        </div>
      </div>
    </div>
  );
}

export function ForbiddenPage() {
  return (
    <div className="status-page">
      <span className="status-page-icon">
        <Icon name="lock" size={30} />
      </span>
      <h1>无权访问该页面</h1>
      <p>当前账号的角色没有该页面的访问权限。如需访问，请联系管理员调整角色。</p>
      <button type="button" className="btn btn-primary btn-md" onClick={() => navigate('/dashboard')}>
        返回总览
      </button>
    </div>
  );
}

export function NotFoundPage({ routePath }: { routePath?: string }) {
  return (
    <div className="status-page">
      <span className="status-page-code">404</span>
      <h1>页面不存在</h1>
      <p>
        无法找到路由 <code>{routePath || '/'}</code>，可能是地址输入有误或页面已下线。
      </p>
      <div className="status-page-actions">
        <button type="button" className="btn btn-primary btn-md" onClick={() => navigate('/dashboard')}>
          返回总览
        </button>
        <button type="button" className="btn btn-secondary btn-md" onClick={() => window.history.back()}>
          返回上一页
        </button>
      </div>
    </div>
  );
}
