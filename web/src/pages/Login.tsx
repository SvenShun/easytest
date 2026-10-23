import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { useAuth } from '../auth';
import { useAppConfig, setConfig, resetConfig, DEFAULT_CONFIG } from '../config';
import { navigate } from '../router';
import { ApiError } from '../api/client';
import { cx, safeErrorMessage, userDisplayName } from '../utils';
import { BrandMark, Icon } from '../components/icons';
import { Button, Field, Input, Pill } from '../components/ui';
import { toastError, toastSuccess } from '../components/Toast';
import { ROLE_META, metaOf } from '../constants';

const DEMO_ACCOUNTS = [
  { username: 'admin', password: 'admin123', role: 'ADMIN' },
  { username: 'tester', password: 'test123', role: 'TESTER' },
  { username: 'business', password: 'biz123', role: 'BUSINESS' },
  { username: 'ops', password: 'ops123', role: 'OPS' },
];

export function LoginPage() {
  const { login, authenticated, user } = useAuth();
  const config = useAppConfig();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<{ username?: string; password?: string }>({});
  const [showSettings, setShowSettings] = useState(false);
  const [coreUrl, setCoreUrl] = useState(config.coreUrl);
  const [uexecUrl, setUexecUrl] = useState(config.uexecUrl);
  const [configError, setConfigError] = useState('');

  useEffect(() => {
    if (authenticated) navigate('/dashboard', { replace: true });
  }, [authenticated]);

  useEffect(() => {
    setCoreUrl(config.coreUrl);
    setUexecUrl(config.uexecUrl);
  }, [config.coreUrl, config.uexecUrl]);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError('');
    const nextErrors: { username?: string; password?: string } = {};
    if (!username.trim()) nextErrors.username = '请输入用户名';
    if (!password) nextErrors.password = '请输入密码';
    setFieldErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;

    setLoading(true);
    try {
      const loggedInUser = await login(username, password);
      toastSuccess(`欢迎回来，${userDisplayName(loggedInUser) || loggedInUser.username}`, '登录成功');
      navigate('/dashboard', { replace: true });
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.code === 'INVALID_CREDENTIALS') {
          setError('用户名或密码错误，请检查后重试');
        } else if (err.code === 'NETWORK_ERROR') {
          setError(err.message);
        } else {
          setError(err.message || '登录失败');
        }
      } else {
        setError(safeErrorMessage(err));
      }
    } finally {
      setLoading(false);
    }
  };

  const fillAccount = (account: (typeof DEMO_ACCOUNTS)[number]) => {
    setUsername(account.username);
    setPassword(account.password);
    setFieldErrors({});
    setError('');
  };

  const saveServiceConfig = () => {
    setConfigError('');
    if (!coreUrl.trim() || !uexecUrl.trim()) {
      setConfigError('两个服务地址都不能为空');
      return;
    }
    setConfig({ coreUrl, uexecUrl });
    toastSuccess('服务地址已保存，后续请求将使用新地址', '配置已更新');
  };

  const resetServiceConfig = () => {
    const next = resetConfig();
    setCoreUrl(next.coreUrl);
    setUexecUrl(next.uexecUrl);
    toastSuccess('已恢复默认服务地址');
  };

  return (
    <div className="login-page">
      <div className="login-bg" aria-hidden="true">
        <span className="login-orb login-orb-a" />
        <span className="login-orb login-orb-b" />
        <span className="login-grid" />
      </div>

      <div className="login-shell">
        <div className="login-card">
          <div className="login-brand">
            <BrandMark size={46} />
            <div>
              <div className="login-brand-name">TestHub</div>
              <div className="login-brand-sub">一站式测试平台</div>
            </div>
          </div>

          <div className="login-heading">
            <h1>欢迎登录</h1>
            <p>需求、用例、计划、执行轨迹与设备池，一个控制台全部搞定。</p>
          </div>

          <form className="login-form" onSubmit={submit} noValidate>
            <Field label="用户名" htmlFor="login-username" required error={fieldErrors.username}>
              <Input
                id="login-username"
                icon="user"
                autoComplete="username"
                placeholder="请输入用户名"
                value={username}
                invalid={Boolean(fieldErrors.username)}
                onChange={(event) => {
                  setUsername(event.target.value);
                  setFieldErrors((prev) => ({ ...prev, username: undefined }));
                }}
              />
            </Field>

            <Field label="密码" htmlFor="login-password" required error={fieldErrors.password}>
              <Input
                id="login-password"
                icon="lock"
                type={showPassword ? 'text' : 'password'}
                autoComplete="current-password"
                placeholder="请输入密码"
                value={password}
                invalid={Boolean(fieldErrors.password)}
                onChange={(event) => {
                  setPassword(event.target.value);
                  setFieldErrors((prev) => ({ ...prev, password: undefined }));
                }}
                suffix={
                  <button
                    type="button"
                    className="input-action"
                    aria-label={showPassword ? '隐藏密码' : '显示密码'}
                    onClick={() => setShowPassword((prev) => !prev)}
                  >
                    <Icon name="eye" size={15} />
                  </button>
                }
              />
            </Field>

            {error ? (
              <div className="login-error" role="alert">
                <Icon name="warning" size={15} />
                <span>{error}</span>
              </div>
            ) : null}

            <Button type="submit" variant="primary" size="lg" block loading={loading} loadingText="登录中…">
              登录
            </Button>
          </form>

          <div className="login-demo">
            <div className="login-demo-title">
              <span>演示账号</span>
              <span className="login-demo-hint">点击自动填充</span>
            </div>
            <div className="demo-grid">
              {DEMO_ACCOUNTS.map((account) => {
                const roleMeta = metaOf(ROLE_META, account.role);
                return (
                  <button
                    type="button"
                    className={cx('demo-account', username === account.username && 'is-selected')}
                    key={account.username}
                    onClick={() => fillAccount(account)}
                  >
                    <span className="demo-account-main">
                      <span className="demo-username">{account.username}</span>
                      <span className="demo-password">{account.password}</span>
                    </span>
                    <Pill tone={roleMeta.tone}>{roleMeta.label}</Pill>
                  </button>
                );
              })}
            </div>
          </div>

          <div className="login-service">
            <button
              type="button"
              className="login-service-toggle"
              onClick={() => setShowSettings((prev) => !prev)}
            >
              <Icon name="server" size={14} />
              服务地址设置
              <Icon name={showSettings ? 'chevron-up' : 'chevron-down'} size={14} />
            </button>
            {showSettings ? (
              <div className="login-service-panel">
                <Field label="Core 服务地址" htmlFor="cfg-core">
                  <Input
                    id="cfg-core"
                    value={coreUrl}
                    placeholder={DEFAULT_CONFIG.coreUrl}
                    onChange={(event) => setCoreUrl(event.target.value)}
                  />
                </Field>
                <Field label="UI Execution 服务地址" htmlFor="cfg-uexec">
                  <Input
                    id="cfg-uexec"
                    value={uexecUrl}
                    placeholder={DEFAULT_CONFIG.uexecUrl}
                    onChange={(event) => setUexecUrl(event.target.value)}
                  />
                </Field>
                {configError ? <div className="field-error">{configError}</div> : null}
                <div className="login-service-actions">
                  <Button size="sm" variant="secondary" icon="rollback" onClick={resetServiceConfig}>
                    恢复默认
                  </Button>
                  <Button size="sm" variant="primary" icon="check" onClick={saveServiceConfig}>
                    保存地址
                  </Button>
                </div>
                <div className="field-hint">地址持久化在浏览器 localStorage，仅在当前浏览器生效。</div>
              </div>
            ) : null}
          </div>
        </div>

        <footer className="login-footer">
          <span>TestHub V1 · 内部测试平台</span>
          <span className="login-footer-dot" />
          <span>v1.0.0</span>
          {user ? <span className="login-footer-user">上次登录：{userDisplayName(user)}</span> : null}
        </footer>
      </div>
    </div>
  );
}
