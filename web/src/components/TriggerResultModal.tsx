import type { TriggerResponse } from '../api/types';
import { EXECUTOR_META, TASK_STATUS_META, metaOf } from '../constants';
import { navigate } from '../router';
import { Icon } from '../components/icons';
import { Button, EmptyState, Pill, StatusPill } from './ui';
import { Modal } from './Modal';

export function TriggerResultModal({
  open,
  result,
  onClose,
}: {
  open: boolean;
  result: TriggerResponse | null;
  onClose: () => void;
}) {
  if (!result) {
    return (
      <Modal open={open} title="触发结果" onClose={onClose} size="md">
        <EmptyState compact icon="play" title="暂无触发结果" />
      </Modal>
    );
  }

  return (
    <Modal
      open={open}
      title="计划已触发"
      description={`运行 ID：${result.runId}`}
      onClose={onClose}
      size="lg"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            关闭
          </Button>
          <Button
            variant="primary"
            icon="arrow-right"
            onClick={() => {
              onClose();
              navigate(`/runs/${encodeURIComponent(result.runId)}`);
            }}
          >
            查看运行报告
          </Button>
        </>
      }
    >
      <div className="form-stack">
        <div className="trigger-result-summary">
          <Pill tone="green">已创建 {result.items.length} 个任务</Pill>
          <span className="text-muted">各用例独立执行，单个用例失败不会中断整个计划。</span>
        </div>
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>用例</th>
                <th>执行器</th>
                <th>任务状态</th>
                <th>任务 / 错误</th>
                <th className="th-right">操作</th>
              </tr>
            </thead>
            <tbody>
              {result.items.length === 0 ? (
                <tr>
                  <td colSpan={5}>
                    <EmptyState compact icon="empty" title="没有创建任务" description="计划中可能没有已通过的用例" />
                  </td>
                </tr>
              ) : (
                result.items.map((item) => (
                  <tr key={`${item.caseId}-${item.taskId || ''}`}>
                    <td>
                      <div className="cell-title sm">{item.caseTitle}</div>
                      <div className="cell-sub mono">{item.caseId}</div>
                    </td>
                    <td>
                      <StatusPill meta={metaOf(EXECUTOR_META, item.executorType)} dot={false} />
                    </td>
                    <td>
                      <StatusPill meta={metaOf(TASK_STATUS_META, item.taskStatus)} />
                    </td>
                    <td>
                      {item.taskId ? (
                        <span className="mono">{item.taskId}</span>
                      ) : (
                        <span className="text-danger">{item.error || '未创建任务'}</span>
                      )}
                      {item.taskId && item.error ? (
                        <div className="cell-sub text-danger">{item.error}</div>
                      ) : null}
                    </td>
                    <td className="td-right">
                      {item.taskId ? (
                        <button
                          type="button"
                          className="link-btn"
                          onClick={() => {
                            onClose();
                            navigate(`/tasks/${encodeURIComponent(item.taskId || '')}`);
                          }}
                        >
                          查看轨迹
                          <Icon name="arrow-right" size={13} />
                        </button>
                      ) : (
                        <span className="text-muted">—</span>
                      )}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </Modal>
  );
}
