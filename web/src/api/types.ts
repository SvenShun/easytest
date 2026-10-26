export type Role = 'ADMIN' | 'TESTER' | 'BUSINESS' | 'OPS';

export type ReviewStatus = 'DRAFT' | 'PENDING' | 'APPROVED' | 'REJECTED';
export type CaseSource = 'MANUAL' | 'AI';
export type ScriptType = 'TESTNG' | 'UIAUTOMATOR2' | 'PYTEST' | 'CUSTOM';
export type ExecutorType = 'SCRIPT' | 'AI_RUNTIME' | 'API' | 'STEP';
export type TaskStatus =
  | 'PENDING'
  | 'DISPATCHED'
  | 'RUNNING'
  | 'SUCCESS'
  | 'FAILED'
  | 'TIMEOUT'
  | 'CANCELLED'
  | 'SKIPPED';
export type DeviceStatus =
  | 'IDLE'
  | 'QUEUED'
  | 'SCRIPT_RUNNING'
  | 'AI_RUNNING'
  | 'OFFLINE'
  | 'MAINTENANCE';
export type PlanRunStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'PARTIAL' | 'FAILED' | string;
export type FailureCategory =
  | 'MODEL_DECISION_FAILED'
  | 'ACTION_FAILED'
  | 'ELEMENT_NOT_FOUND'
  | 'TIMEOUT'
  | 'MANUAL_INTERRUPT'
  | 'EXECUTOR_UNAVAILABLE'
  | 'SKILLS_UNAVAILABLE'
  | 'SCRIPT_FAILED'
  | string;

export interface Page<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}

export interface User {
  id?: string;
  username: string;
  displayName?: string;
  name?: string;
  role: Role;
  status?: string;
  enabled?: boolean;
  createdAt?: string;
}

export interface UserCreateInput {
  username: string;
  password: string;
  displayName?: string;
  role?: Role;
  enabled?: boolean;
}

export interface UserUpdateInput {
  displayName?: string;
  role?: Role;
  enabled?: boolean;
}

export interface LoginResponse {
  token: string;
  expiresInSec?: number;
  user: User;
}

export interface Requirement {
  id: string;
  title: string;
  description?: string;
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ScriptInfo {
  id: string;
  name: string;
  scriptType: ScriptType;
  entryCommand?: string;
  sizeBytes?: number;
  createdBy?: string;
  createdAt?: string;
}

export interface CaseScriptRef {
  id: string;
  name: string;
  scriptType: ScriptType;
  entryCommand?: string;
}

export interface CaseCapability {
  availableExecutors: ExecutorType[];
  defaultExecutor?: ExecutorType;
}

export interface CaseReview {
  id: string;
  action: string;
  actor?: string;
  comment?: string;
  createdAt?: string;
}

export interface FailureAnalysis {
  reportUrl?: string;
  summary?: string;
  reasonCategory?: FailureCategory;
  taskId?: string;
  updatedAt?: string;
  [key: string]: unknown;
}

export interface CaseDetail {
  id: string;
  title: string;
  given: string;
  when: string;
  then: string;
  requirementId?: string | null;
  requirementTitle?: string | null;
  reviewStatus: ReviewStatus;
  source: CaseSource;
  modelId?: string | null;
  script?: CaseScriptRef | null;
  scriptId?: string | null;
  scriptRef?: unknown;
  failureAnalysis?: FailureAnalysis | string | null;
  failureReportUrl?: string | null;
  capability?: CaseCapability;
  executionMode?: ExecutionMode;
  steps?: CaseStep[];
  reviews?: CaseReview[];
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string;
}

export type ExecutionMode = 'AI' | 'STEPS';
export type StepType =
  | 'CLICK' | 'INPUT' | 'TAP' | 'SWIPE' | 'BACK' | 'HOME' | 'WAIT'
  | 'LAUNCH' | 'ASSERT_TEXT' | 'ASSERT_EXISTS' | 'SCREENSHOT';
export type SelectorType = 'TEXT' | 'ID' | 'COORD';

export interface CaseStep {
  id?: string;
  seq?: number;
  stepType: StepType;
  selectorType?: SelectorType;
  selector?: string;
  value?: string;
  assertValue?: string;
  timeoutMs?: number;
  enabled?: boolean;
}

export interface CaseInput {
  title: string;
  given: string;
  when: string;
  then: string;
  requirementId: string | null;
  scriptId: string | null;
  source?: CaseSource;
  executionMode?: ExecutionMode;
  steps?: CaseStep[];
}

export type ReviewAction = 'SUBMIT' | 'APPROVE' | 'REJECT';

export interface AiDraft {
  caseId: string;
  title: string;
  given: string;
  when: string;
  then: string;
  reviewStatus?: ReviewStatus;
}

export interface AiDraftResponse {
  jobId: string;
  status: string;
  modelId?: string;
  drafts: AiDraft[];
  error?: string | null;
}

export interface AiJob {
  id: string;
  requirementId?: string | null;
  modelId?: string | null;
  promptVersion?: string;
  status?: string;
  outputCaseIds?: string[];
  error?: string | null;
  createdAt?: string;
  finishedAt?: string;
}

export interface Plan {
  id: string;
  name: string;
  description?: string;
  environmentId?: string | null;
  status?: string;
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string;
  caseCount?: number;
  runCount?: number;
  lastRunStatus?: string;
  lastStatus?: string;
}

export interface ApiCase {
  id: string;
  name: string;
  method: string;
  baseUrl?: string | null;
  path: string;
  headers?: Record<string, string>;
  query?: Record<string, string>;
  body?: string | null;
  assertions?: Array<Record<string, unknown>>;
  dataset?: Array<Record<string, unknown>>;
  timeoutSec: number;
  enabled: boolean;
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ApiCaseInput {
  name: string;
  method: string;
  baseUrl?: string;
  path: string;
  headers?: Record<string, string>;
  query?: Record<string, string>;
  body?: string;
  assertions?: Array<Record<string, unknown>>;
  dataset?: Array<Record<string, unknown>>;
  timeoutSec?: number;
  enabled?: boolean;
}

export interface ApiAuthConfig {
  type: string;
  token?: string;
  username?: string;
  password?: string;
  headerName?: string;
  headerValue?: string;
  prefix?: string;
  loginMethod?: string;
  loginPath?: string;
  loginHeaders?: Record<string, string>;
  loginBody?: string;
  tokenPath?: string;
  tokenHeader?: string;
  tokenPrefix?: string;
}

export interface ApiEnvironment {
  id: string;
  name: string;
  baseUrl: string;
  headers?: Record<string, string>;
  variables?: Record<string, string>;
  auth?: ApiAuthConfig;
  enabled: boolean;
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ApiEnvironmentInput {
  name: string;
  baseUrl: string;
  headers?: Record<string, string>;
  variables?: Record<string, string>;
  auth?: ApiAuthConfig;
  enabled?: boolean;
}

export interface TestJob {
  id: string;
  name: string;
  planId: string;
  planName?: string;
  cronExpression: string;
  deviceId?: string | null;
  deviceTags?: string | null;
  deviceIds?: string[];
  enabled: boolean;
  lastRunId?: string | null;
  lastRunAt?: string | null;
  nextRunAt?: string | null;
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface TestJobInput {
  name: string;
  planId: string;
  cronExpression: string;
  deviceId?: string | null;
  deviceTags?: string | null;
  deviceIds?: string[];
  enabled?: boolean;
}

export interface PlanCase {
  caseId: string;
  title: string;
  reviewStatus: ReviewStatus;
  hasScript?: boolean;
  scriptType?: ScriptType | null;
  executorTypeOverride?: ExecutorType | null;
  defaultExecutor?: ExecutorType | null;
  availableExecutors?: ExecutorType[];
  caseType?: 'UI' | 'API';
  method?: string | null;
  path?: string | null;
}

export interface PlanRun {
  id: string;
  planId?: string;
  status: PlanRunStatus;
  triggeredBy?: string;
  startedAt?: string | null;
  finishedAt?: string | null;
  total?: number;
  success?: number;
  failed?: number;
  timeout?: number;
  cancelled?: number;
  running?: number;
  pending?: number;
}

export interface PlanDetail {
  plan: Plan;
  cases: PlanCase[];
  runs: PlanRun[];
}

export interface TriggerItem {
  caseId: string;
  caseTitle: string;
  taskId?: string | null;
  taskStatus?: TaskStatus | string;
  executorType?: ExecutorType | string;
  error?: string | null;
}

export interface TriggerResponse {
  runId: string;
  items: TriggerItem[];
}

export interface TriggerPayload {
  targetDeviceId: string | null;
  targetTags: string[];
  timeoutSec: number;
  stepTimeoutSec: number;
  targetDeviceIds?: string[];
}

export interface RunItem {
  caseId: string;
  caseTitle: string;
  taskId?: string | null;
  executorType?: ExecutorType | string;
  status: TaskStatus | string;
  failureReason?: FailureCategory | null;
  failureReportUrl?: string | null;
  summary?: string | null;
  deviceId?: string | null;
  updatedAt?: string | null;
}

export interface RunSummary {
  total: number;
  success: number;
  failed: number;
  timeout: number;
  cancelled: number;
  running: number;
  pending: number;
  skipped?: number;
}

export interface ExecutorStat {
  executorType: ExecutorType | string;
  total: number;
  success: number;
  failed: number;
}

export interface FailureCategoryStat {
  category: FailureCategory;
  count: number;
}

export interface RunReport {
  run: PlanRun;
  summary: RunSummary;
  executors?: ExecutorStat[];
  failureCategories?: FailureCategoryStat[];
  items?: RunItem[];
}

export interface TaskStep {
  stepId?: string;
  seq: number;
  status: string;
  decision?: string | null;
  action?: string | null;
  elapsedMs?: number | null;
  screenshotUrl?: string | null;
  createdAt?: string;
}

export interface TaskLog {
  seq: number;
  level?: string;
  message?: string;
  createdAt?: string;
}

export interface TaskArtifact {
  name: string;
  type?: string;
  url?: string;
}

export interface FailureReport {
  reportUrl?: string;
  reasonCategory?: FailureCategory | null;
  summary?: string | null;
}

export interface SkillRef {
  repo?: string;
  commit?: string;
}

export interface TaskSummary {
  taskId: string;
  planId?: string | null;
  planRunId?: string | null;
  caseId?: string | null;
  caseTitle?: string | null;
  executorType?: ExecutorType | string | null;
  status: TaskStatus;
  failureReason?: FailureCategory | null;
  failureSummary?: string | null;
  failureReportUrl?: string | null;
  queuePosition?: number;
  deviceId?: string | null;
  deviceSerial?: string | null;
  agentId?: string | null;
  createdAt?: string;
  dispatchedAt?: string | null;
  startedAt?: string | null;
  finishedAt?: string | null;
  durationMs?: number | null;
}

export interface TaskDetail extends TaskSummary {
  bdd?: { given?: string; when?: string; then?: string } | null;
  skills?: SkillRef | null;
  timeoutSec?: number;
  stepTimeoutSec?: number;
  steps?: TaskStep[];
  logs?: TaskLog[];
  artifacts?: TaskArtifact[];
  failureReport?: FailureReport | null;
}

export interface Device {
  deviceId: string;
  serial: string;
  name?: string;
  model?: string;
  platform?: string;
  tags?: string[];
  status: DeviceStatus;
  online?: boolean;
  currentTaskId?: string | null;
  queueLength?: number;
  agentId?: string | null;
  agentName?: string | null;
  lastSeenAt?: string;
  occupant?: string | null;
  occupiedUntil?: string | null;
}

export interface DeviceQueueTask {
  taskId: string;
  position: number;
  caseTitle?: string;
  status: TaskStatus;
  executorType?: ExecutorType | string;
  createdAt?: string;
}

export interface DeviceQueue {
  deviceId: string;
  serial?: string;
  queueLength: number;
  tasks: DeviceQueueTask[];
}

export interface Agent {
  agentId: string;
  name: string;
  host?: string;
  baseUrl?: string;
  status?: string;
  lastHeartbeat?: string;
  devices?: Device[];
}

export interface DeviceStats {
  devices?: {
    total: number;
    online: number;
    idle: number;
    running: number;
    offline: number;
  };
  tasks?: {
    pending: number;
    running: number;
    success: number;
    failed: number;
    timeout: number;
    cancelled: number;
  };
  failureCategories?: FailureCategoryStat[];
  skills?: {
    configured?: boolean;
    activeCommit?: string | null;
  };
}

export interface ModelConfig {
  configType: 'text' | 'vision' | string;
  provider: string;
  endpoint?: string;
  modelId?: string;
  apiKeyMasked?: string;
  configured?: boolean;
  updatedBy?: string;
  updatedAt?: string;
}

export interface ModelConfigInput {
  provider: string;
  endpoint: string;
  apiKey: string;
  modelId: string;
}

export interface SkillsPointer {
  gitUrl?: string;
  branch?: string;
  activeCommit?: string | null;
  updatedBy?: string;
  updatedAt?: string;
  syncedAt?: string;
  configured?: boolean;
}

export interface AuditLog {
  id: string;
  actor?: string;
  action?: string;
  target?: string;
  detail?: unknown;
  createdAt?: string;
}
