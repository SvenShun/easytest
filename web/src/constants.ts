import type { FailureCategory, Role } from './api/types';

export type Tone = 'green' | 'red' | 'orange' | 'gray' | 'blue' | 'purple' | 'slate';

export interface StatusMeta {
  label: string;
  tone: Tone;
}

export const REVIEW_STATUS_META: Record<string, StatusMeta> = {
  DRAFT: { label: '草稿', tone: 'gray' },
  PENDING: { label: '待评审', tone: 'orange' },
  APPROVED: { label: '已通过', tone: 'green' },
  REJECTED: { label: '已驳回', tone: 'red' },
};

export const TASK_STATUS_META: Record<string, StatusMeta> = {
  PENDING: { label: '排队中', tone: 'slate' },
  DISPATCHED: { label: '已下发', tone: 'blue' },
  RUNNING: { label: '执行中', tone: 'blue' },
  SUCCESS: { label: '成功', tone: 'green' },
  FAILED: { label: '失败', tone: 'red' },
  TIMEOUT: { label: '超时', tone: 'orange' },
  CANCELLED: { label: '已取消', tone: 'gray' },
  SKIPPED: { label: '已跳过', tone: 'gray' },
};

export const DEVICE_STATUS_META: Record<string, StatusMeta> = {
  IDLE: { label: '空闲', tone: 'green' },
  QUEUED: { label: '排队中', tone: 'blue' },
  SCRIPT_RUNNING: { label: '脚本执行中', tone: 'blue' },
  AI_RUNNING: { label: 'AI 执行中', tone: 'purple' },
  OFFLINE: { label: '离线', tone: 'gray' },
  MAINTENANCE: { label: '维护', tone: 'orange' },
};

export const STEP_STATUS_META: Record<string, StatusMeta> = {
  OK: { label: '成功', tone: 'green' },
  SUCCESS: { label: '成功', tone: 'green' },
  FAIL: { label: '失败', tone: 'red' },
  FAILED: { label: '失败', tone: 'red' },
  RUNNING: { label: '执行中', tone: 'blue' },
  PENDING: { label: '等待', tone: 'slate' },
  SKIPPED: { label: '跳过', tone: 'gray' },
};

export const PLAN_RUN_STATUS_META: Record<string, StatusMeta> = {
  PENDING: { label: '排队中', tone: 'slate' },
  RUNNING: { label: '执行中', tone: 'blue' },
  COMPLETED: { label: '已完成', tone: 'green' },
  PARTIAL: { label: '部分失败', tone: 'orange' },
  FAILED: { label: '失败', tone: 'red' },
  SUCCESS: { label: '成功', tone: 'green' },
  TIMEOUT: { label: '超时', tone: 'orange' },
  CANCELLED: { label: '已取消', tone: 'gray' },
};

export const REVIEW_ACTION_META: Record<string, StatusMeta> = {
  SUBMIT: { label: '提交评审', tone: 'blue' },
  SUBMITTED: { label: '提交评审', tone: 'blue' },
  APPROVE: { label: '评审通过', tone: 'green' },
  APPROVED: { label: '评审通过', tone: 'green' },
  REJECT: { label: '评审驳回', tone: 'red' },
  REJECTED: { label: '评审驳回', tone: 'red' },
};

export const FAILURE_CATEGORY_META: Record<string, StatusMeta> = {
  MODEL_DECISION_FAILED: { label: '模型决策失败', tone: 'purple' },
  ACTION_FAILED: { label: '动作执行失败', tone: 'red' },
  ELEMENT_NOT_FOUND: { label: '元素未找到', tone: 'orange' },
  TIMEOUT: { label: '超时', tone: 'orange' },
  MANUAL_INTERRUPT: { label: '人工中断', tone: 'gray' },
  EXECUTOR_UNAVAILABLE: { label: '执行器不可用', tone: 'slate' },
  SKILLS_UNAVAILABLE: { label: 'Skills 不可用', tone: 'slate' },
  SCRIPT_FAILED: { label: '脚本执行失败', tone: 'red' },
};

export const EXECUTOR_META: Record<string, StatusMeta> = {
  SCRIPT: { label: '传统脚本', tone: 'blue' },
  AI_RUNTIME: { label: 'AI Runtime', tone: 'purple' },
  API: { label: '接口执行', tone: 'slate' },
  STEP: { label: '步骤化执行', tone: 'blue' },
  SCENARIO: { label: 'API 场景', tone: 'purple' },
};

export const SOURCE_META: Record<string, StatusMeta> = {
  MANUAL: { label: '人工', tone: 'slate' },
  AI: { label: 'AI', tone: 'purple' },
};

export const ROLE_META: Record<Role | string, StatusMeta> = {
  ADMIN: { label: '管理员', tone: 'purple' },
  TESTER: { label: '测试工程师', tone: 'blue' },
  BUSINESS: { label: '业务人员', tone: 'green' },
  OPS: { label: '运维人员', tone: 'orange' },
};

export const SCRIPT_TYPE_LABEL: Record<string, string> = {
  TESTNG: 'TestNG',
  UIAUTOMATOR2: 'uiautomator2',
  PYTEST: 'Pytest',
  CUSTOM: '自定义',
};

export const JOB_STATUS_META: Record<string, StatusMeta> = {
  SUCCEEDED: { label: '成功', tone: 'green' },
  SUCCESS: { label: '成功', tone: 'green' },
  FAILED: { label: '失败', tone: 'red' },
  RUNNING: { label: '执行中', tone: 'blue' },
  PENDING: { label: '排队中', tone: 'slate' },
};

export const AGENT_STATUS_META: Record<string, StatusMeta> = {
  ONLINE: { label: '在线', tone: 'green' },
  UP: { label: '在线', tone: 'green' },
  HEALTHY: { label: '在线', tone: 'green' },
  OFFLINE: { label: '离线', tone: 'gray' },
  DISABLED: { label: '已停用', tone: 'red' },
  DOWN: { label: '离线', tone: 'gray' },
  UNKNOWN: { label: '未知', tone: 'gray' },
};

export const MODEL_PROVIDERS = ['mock', 'openai', 'deepseek'] as const;

export function metaOf(map: Record<string, StatusMeta>, value?: string | null): StatusMeta {
  if (!value) return { label: '—', tone: 'gray' };
  return map[value] ?? { label: value, tone: 'gray' };
}

export function failureLabel(value?: FailureCategory | null): string {
  if (!value) return '—';
  return FAILURE_CATEGORY_META[value]?.label ?? value;
}

export const TERMINAL_TASK_STATUSES = ['SUCCESS', 'FAILED', 'TIMEOUT', 'CANCELLED', 'SKIPPED'];
