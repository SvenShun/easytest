import { useEffect, useState } from 'react';
import { dashboardApi } from '../api';
import type { DashboardSummary } from '../api/dashboard';
import { navigate } from '../router';
import { formatDateTime, safeErrorMessage } from '../utils';
import { Button, Card, CardHead, EmptyState, ErrorPanel, LoadingPanel, PageHeading, Pill } from '../components/ui';

export function ReportsPage() {
  const [data, setData] = useState<DashboardSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = () => {
    setLoading(true);
    setError('');
    dashboardApi.summary()
      .then(setData)
      .catch((err) => setError(safeErrorMessage(err)))
      .finally(() => setLoading(false));
  };

  useEffect(load, []);

  if (loading && !data) return <LoadingPanel text="正在加载测试报告…" />;

  return (
    <div className="page-stack">
      <PageHeading
        title="测试报告"
        description="用例资产、真机执行通过率、最近运行与失败分类。"
        actions={<Button variant="secondary" icon="refresh" loading={loading} onClick={load}>刷新</Button>}
      />
      {error ? <ErrorPanel message={error} onRetry={load} /> : null}
      {data ? (
        <>
          <div className="summary-row">
            <div className="summary-chip"><span className="summary-chip-label">需求</span><span className="summary-chip-value">{data.requirements}</span></div>
            <div className="summary-chip"><span className="summary-chip-label">UI 用例</span><span className="summary-chip-value">{data.cases}</span></div>
            <div className="summary-chip"><span className="summary-chip-label">已评审</span><span className="summary-chip-value">{data.approvedCases}</span></div>
            <div className="summary-chip"><span className="summary-chip-label">接口用例</span><span className="summary-chip-value">{data.apiCases}</span></div>
            <div className="summary-chip"><span className="summary-chip-label">计划</span><span className="summary-chip-value">{data.plans}</span></div>
            <div className="summary-chip"><span className="summary-chip-label">运行</span><span className="summary-chip-value">{data.runs}</span></div>
            <div className="summary-chip"><span className="summary-chip-label">通过率</span><span className="summary-chip-value text-success">{data.passRate}%</span></div>
          </div>
          <div className="summary-row">
            <div className="summary-chip"><span className="summary-chip-label">成功</span><span className="summary-chip-value text-success">{data.success}</span></div>
            <div className="summary-chip"><span className="summary-chip-label">失败</span><span className="summary-chip-value text-danger">{data.failed}</span></div>
            <div className="summary-chip"><span className="summary-chip-label">超时</span><span className="summary-chip-value">{data.timeout}</span></div>
            <div className="summary-chip"><span className="summary-chip-label">取消</span><span className="summary-chip-value">{data.cancelled}</span></div>
            <div className="summary-chip"><span className="summary-chip-label">执行项</span><span className="summary-chip-value">{data.totalItems}</span></div>
          </div>

          <Card>
            <CardHead title="最近运行" description="点击查看运行详情与步骤轨迹" />
            <div className="table-wrap">
              <table className="table">
                <thead><tr><th>运行</th><th>计划</th><th>状态</th><th>总数</th><th>成功</th><th>失败</th><th>开始时间</th></tr></thead>
                <tbody>
                  {data.recentRuns.length === 0 ? (
                    <tr><td colSpan={7}><EmptyState compact icon="plans" title="暂无运行" description="触发一个测试计划后在这里查看" /></td></tr>
                  ) : data.recentRuns.map((run) => (
                    <tr key={run.runId} className="clickable-row" onClick={() => navigate(`/runs/${run.runId}`)}>
                      <td className="mono">{run.runId}</td>
                      <td>{run.planId}</td>
                      <td><Pill tone={run.status === 'COMPLETED' ? 'green' : run.status === 'FAILED' ? 'red' : 'blue'} dot>{run.status}</Pill></td>
                      <td>{run.total}</td><td>{run.success}</td><td>{run.failed}</td>
                      <td>{formatDateTime(run.startedAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>

          <Card>
            <CardHead title="失败分类" description="最近 30 天运行项" />
            <div className="card-body">
              {data.failures.length === 0 ? <span className="text-muted">暂无失败记录</span> : data.failures.map((item) => (
                <div key={item.reason} className="failure-bar">
                  <span className="mono">{item.reason}</span>
                  <span className="failure-bar-track"><span style={{ width: `${Math.min(100, item.count * 10)}%` }} /></span>
                  <strong>{item.count}</strong>
                </div>
              ))}
            </div>
          </Card>
        </>
      ) : null}
    </div>
  );
}
