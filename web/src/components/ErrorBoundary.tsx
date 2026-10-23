import { Component } from 'react';
import type { ErrorInfo, ReactNode } from 'react';
import { BrandMark, Icon } from './icons';

interface Props {
  children: ReactNode;
}

interface State {
  error: Error | null;
}

export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('[TestHub] 页面渲染异常', error, info);
  }

  render() {
    if (!this.state.error) return this.props.children;
    return (
      <div className="fatal-screen">
        <div className="fatal-card">
          <BrandMark size={44} />
          <div className="fatal-icon">
            <Icon name="warning" size={22} />
          </div>
          <h1>页面渲染遇到问题</h1>
          <p className="fatal-message">{this.state.error.message || '发生了未知错误'}</p>
          <p className="fatal-hint">可以尝试重新加载页面；若持续出现，请检查浏览器控制台或联系管理员。</p>
          <div className="fatal-actions">
            <button type="button" className="btn btn-primary btn-md" onClick={() => window.location.reload()}>
              重新加载
            </button>
            <button
              type="button"
              className="btn btn-secondary btn-md"
              onClick={() => {
                window.location.hash = '#/dashboard';
                this.setState({ error: null });
              }}
            >
              返回总览
            </button>
          </div>
        </div>
      </div>
    );
  }
}
