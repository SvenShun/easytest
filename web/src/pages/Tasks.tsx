import { useCallback, useEffect, useMemo, useState } from 'react';
import { uiexecApi } from '../api';
import type { TaskSummary } from '../api/types';
import { EXECUTOR_META, TASK_STATUS_META, metaOf } from '../constants';
import { useDebouncedValue } from '../hooks';
import { navigate, useHashRoute } from '../router';
import { formatDateTime, isTerminalTask, safeErrorMessage } from '../utils';
import { Icon } from '../components/icons';
import {
  Button,
  Card,
  EmptyState,
  ErrorPanel,
  Input,
  PageHeading,
  Pagination,
  Select,
  SkeletonRows,
  StatusPill,
} from '../components/ui';

const PAGE_SIZE = 10;
const FETCH_SIZE = 100;

const STATUS_OPTIONS = [
  { value: '', label: '全部状态' },
  { value: 'PENDING', label: '排队中' },
  { value: 'DISPATCHED', label: '已下发' },
  { value: 'RUNNING', label: '执行中' },
  { value: 'SUCCESS', label: '成功' },
  { value: 'FAILED', label: '失败' },
  { value: 'TIMEOUT', label: '超时' },
  { value: 'CANCELLED', label: '已取消' },
];

const EXECUTOR_OPTIONS = [
  { value: '', label: '全部执行器' },
  { value: 'SCRIPT', label: '传统脚本' },
  { value: 'AI_RUNTIME', label: 'AI Runtime' },
];

export function TasksPage() {
  const route = useHashRoute();
  const initialPlanRunId = route.query.get('planRunId') || '';

  const [tasks, setTasks] = useState<TaskSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState('');
  const [status, setStatus] = useState('');
  const [executor, setExecutor] = useState('');
  const [planRunInput, setPlanRunInput] = useState(initialPlanRunId);
  const [planRunId, setPlanRunId] = useState(initialPlanRunId);
  const [keywordInput, setKeywordInput] = useState('');
  const [page, setPage] = useState(1);

  const debouncedKeyword = useDebouncedValue(keywordInput, 300);
  const keyword = debouncedKeyword.trim().toLowerCase();

  const load = useCallback(
    async (silent = false) => {
      if (!silent) setLoading(true);
      else setRefreshing(true);
      setError('');
      try {
        const data = await uiexecApi.tasks({
          page: 1,
          size: FETCH_SIZE,
          status: status || undefined,
          planRunId: planRunId || undefined,
        });
        setTasks(data.items);
      } catch (err) {
        setError(safeErrorMessage(err));
      } finally {
        setLoading(false);
        setRefreshing(false);
      }
    },
    [status, planRunId],
  );

  useEffect(() => {
    setPage(1);
    void load();
  }, [load]);

  const filtered = useMemo(() => {
    return tasks.filter((task) => {
      if (executor && task.executorType !== executor) return false;
      if (keyword) {
        const haystack = [
          task.caseTitle,
          task.taskId,
          task.caseId,
          task.deviceSerial,
          task.planRunId,
          task.planId,
        ]
          .filter(Boolean)
          .join(' ')
          .toLowerCase();
        if (!haystack.includes(keyword)) return false;
      }
      return true;
    });
  }, [tasks, executor, keyword]);

  const pageItems = filtered.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

  const resetFilters = () => {
    setStatus('');
    setExecutor('');
    setPlanRunInput('');
    setPlanRunId('');
    setKeywordInput('');
    setPage(1);
  };

  const hasFilters = Boolean(status || executor || planRunId || keywordInput);

  return (
    <div className="page-stack">
      <PageHeading
        title="执行任务"
        description="查看 ui-execution 中的任务状态与明细；执行器与关键字为前端过滤，状态与计划运行 ID 由后端过滤。"
        actions={
          <div className="page-actions">
            <Button variant="secondary" icon="refresh" loading={refreshing} onClick={() => void load(true)}>
              刷新
            </Button>
          </div>
        }
      />

      <Card>
        <div className="filter-bar is-wrap">
          <div className="filter-fields">
            <div className="filter-item">
              <span className="filter-label">状态</span>
              <Select
                value={status}
                onChange={(event) => {
                  setStatus(event.target.value);
                  setPage(1);
                }}
              >
                {STATUS_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </Select>
            </div>
            <div className="filter-item">
              <span className="filter-label">执行器</span>
              <Select
                value={executor}
                onChange={(event) => {
                  setExecutor(event.target.value);
                  setPage(1);
                }}
              >
                {EXECUTOR_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </Select>
            </div>
            <div className="filter-item filter-plan-run">
              <span className="filter-label">计划运行 ID</span>
              <Input
                placeholder="run-xxxx，回车应用"
                value={planRunInput}
                onChange={(event) => setPlanRunInput(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter') {
                    setPlanRunId(planRunInput.trim());
                    setPage(1);
                  }
                }}
              />
            </div>
            <div className="filter-item filter-grow">
              <span className="filter-label">关键字</span>
              <Input
                icon="search"
                placeholder="用例标题 / 任务 ID / 设备 serial"
                value={keywordInput}
                onChange={(event) => {
                  setKeywordInput(event.target.value);
                  setPage(1);
                }}
              />
            </div>
          </div>
          <div className="filter-actions">
            <Button
              size="sm"
              variant="secondary"
              onClick={() => {
                setPlanRunId(planRunInput.trim());
                setPage(1);
              }}
            >
              应用
            </Button>
            <Button size="sm" variant="ghost" icon="close" disabled={!hasFilters} onClick={resetFilters}>
              清空筛选
            </Button>
          </div>
        </div>

        <div className="card-body no-pad">
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>任务</th>
                  <th>用例</th>
                  <th>执行器</th>
                  <th>状态</th>
                  <th>设备</th>
                  <th>计划运行</th>
                  <th>创建时间</th>
                  <th className="th-right">操作</th>
                </tr>
              </thead>
              <tbody>
                {loading ? (
                  <SkeletonRows rows={6} cols={8} />
                ) : error ? (
                  <tr>
                    <td colSpan={8}>
                      <ErrorPanel
                        message={error}
                        onRetry={() => void load()}
                        hint="请确认 ui-execution（默认 8081）服务已启动。"
                      />
                    </td>
                  </tr>
                ) : pageItems.length === 0 ? (
                  <tr>
                    <td colSpan={8}>
                      <EmptyState
                        icon="terminal"
                        title={hasFilters ? '没有匹配的任务' : '还没有执行任务'}
                        description={
                          hasFilters
                            ? '尝试调整筛选条件或清空关键字；计划运行 ID 需完整匹配'
                            : '在测试计划中触发运行后，任务会出现在这里'
                        }
                        action={
                          hasFilters ? (
                            <Button size="sm" variant="secondary" icon="close" onClick={resetFilters}>
                              清空筛选
                            </Button>
                          ) : (
                            <Button size="sm" variant="primary" icon="plans" onClick={() => navigate('/plans')}>
                              去测试计划
                            </Button>
                          )
                        }
                      />
                    </td>
                  </tr>
                ) : (
                  pageItems.map((task) => (
                    <tr
                      key={task.taskId}
                      className="row-clickable"
                      onClick={() => navigate(`/tasks/${encodeURIComponent(task.taskId)}`)}
                    >
                      <td>
                        <div className="cell-title sm mono">{task.taskId}</div>
                        <div className="cell-sub">
                          {isTerminalTask(task.status) ? '已结束' : '执行中'}
                          {task.queuePosition ? ` · 队列位置 ${task.queuePosition}` : ''}
                        </div>
                      </td>
                      <td>
                        <div className="cell-title">{task.caseTitle || '—'}</div>
                        <div className="cell-sub mono">{task.caseId || ''}</div>
                      </td>
                      <td>
                        <StatusPill meta={metaOf(EXECUTOR_META, task.executorType)} dot={false} />
                      </td>
                      <td>
                        <StatusPill meta={metaOf(TASK_STATUS_META, task.status)} />
                      </td>
                      <td>
                        {task.deviceSerial ? (
                          <span className="mono">{task.deviceSerial}</span>
                        ) : (
                          <span className="text-muted">未分配</span>
                        )}
                      </td>
                      <td>
                        {task.planRunId ? (
                          <span className="mono">{task.planRunId}</span>
                        ) : (
                          <span className="text-muted">—</span>
                        )}
                      </td>
                      <td className="text-muted">{formatDateTime(task.createdAt)}</td>
                      <td className="td-right">
                        <button
                          type="button"
                          className="link-btn"
                          onClick={(event) => {
                            event.stopPropagation();
                            navigate(`/tasks/${encodeURIComponent(task.taskId)}`);
                          }}
                        >
                          查看轨迹
                          <Icon name="arrow-right" size={13} />
                        </button>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
          <div className="table-foot">
            <div className="table-foot-note">
              已加载 {tasks.length} 条任务，过滤后 {filtered.length} 条
            </div>
            <Pagination
              page={page}
              size={PAGE_SIZE}
              total={filtered.length}
              disabled={loading}
              onChange={setPage}
            />
          </div>
        </div>
      </Card>
    </div>
  );
}
