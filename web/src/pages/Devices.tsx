import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { PointerEvent as ReactPointerEvent } from 'react';
import { deviceGroupsApi, uiexecApi } from '../api';
import type { DeviceGroup } from '../api/deviceGroups';
import type { Agent, Device, DeviceQueue } from '../api/types';
import { AGENT_STATUS_META, DEVICE_STATUS_META, EXECUTOR_META, TASK_STATUS_META, metaOf } from '../constants';
import { usePolling } from '../hooks';
import { navigate, useHashRoute } from '../router';
import { cx, formatDateTime, safeErrorMessage, shortId, truncate } from '../utils';
import { Icon } from '../components/icons';
import {
  Button,
  Card,
  CardHead,
  EmptyState,
  ErrorPanel,
  Field,
  Input,
  LoadingPanel,
  PageHeading,
  Pill,
  Select,
  SkeletonRows,
  StatusPill,
} from '../components/ui';
import { ConfirmDialog, Modal, PromptDialog } from '../components/Modal';
import { toastError, toastSuccess } from '../components/Toast';

const POLL_MS = 10000;

const DEVICE_FILTERS = [
  { value: '', label: '全部状态' },
  { value: 'IDLE', label: '空闲' },
  { value: 'QUEUED', label: '排队中' },
  { value: 'SCRIPT_RUNNING', label: '脚本执行中' },
  { value: 'AI_RUNNING', label: 'AI 执行中' },
  { value: 'OFFLINE', label: '离线' },
  { value: 'MAINTENANCE', label: '维护' },
];

function MetricChart({ samples }: { samples: Array<{ createdAt: string; cpuPercent?: number; memUsedPercent?: number; batteryLevel?: number }> }) {
  if (samples.length === 0) {
    return <div className="metric-chart-empty">开始采样后显示 CPU / 内存曲线</div>;
  }
  const width = 620;
  const height = 150;
  const pad = 24;
  const points = (key: 'cpuPercent' | 'memUsedPercent') => samples.map((sample, index) => {
    const value = Math.max(0, Math.min(100, Number(sample[key] || 0)));
    const x = samples.length === 1 ? pad : pad + (index / (samples.length - 1)) * (width - pad * 2);
    const y = height - pad - (value / 100) * (height - pad * 2);
    return `${x},${y}`;
  }).join(' ');
  const last = samples[samples.length - 1];
  return (
    <div className="metric-chart-wrap">
      <div className="metric-chart-legend">
        <span className="legend-cpu">CPU {last.cpuPercent ?? '—'}%</span>
        <span className="legend-mem">内存 {last.memUsedPercent ?? '—'}%</span>
        <span className="text-muted">电量 {last.batteryLevel ?? '—'}%</span>
      </div>
      <svg viewBox={`0 0 ${width} ${height}`} className="metric-chart" role="img" aria-label="性能曲线">
        {[0, 25, 50, 75, 100].map((tick) => (
          <g key={tick}>
            <line x1={pad} x2={width - pad} y1={height - pad - tick / 100 * (height - pad * 2)}
                  y2={height - pad - tick / 100 * (height - pad * 2)} className="metric-grid" />
            <text x={4} y={height - pad - tick / 100 * (height - pad * 2) + 4} className="metric-axis">{tick}</text>
          </g>
        ))}
        <polyline points={points('cpuPercent')} className="metric-line-cpu" />
        <polyline points={points('memUsedPercent')} className="metric-line-mem" />
      </svg>
    </div>
  );
}

export function DevicesPage() {
  const route = useHashRoute();
  const [devices, setDevices] = useState<Device[]>([]);
  const [agents, setAgents] = useState<Agent[]>([]);
  const [devicesLoading, setDevicesLoading] = useState(true);
  const [agentsLoading, setAgentsLoading] = useState(true);
  const [devicesError, setDevicesError] = useState('');
  const [agentsError, setAgentsError] = useState('');
  const [refreshing, setRefreshing] = useState(false);
  const [statusFilter, setStatusFilter] = useState('');
  const [keyword, setKeyword] = useState('');
  const [updatedAt, setUpdatedAt] = useState<Date | null>(null);

  const [manageDevice, setManageDevice] = useState<Device | null>(null);
  const [manageShot, setManageShot] = useState('');
  const [manageError, setManageError] = useState('');
  const [manageLoading, setManageLoading] = useState(false);
  const [deviceSize, setDeviceSize] = useState({ width: 1080, height: 2340 });
  const pointerStart = useRef<{ x: number; y: number } | null>(null);
  const [apkFile, setApkFile] = useState<File | null>(null);
  const [installBusy, setInstallBusy] = useState(false);
  const [appPackage, setAppPackage] = useState('com.testhub.demo');
  const [actionBusy, setActionBusy] = useState(false);
  const [tapX, setTapX] = useState('540');
  const [tapY, setTapY] = useState('1200');
  const [inputTarget, setInputTarget] = useState('用户名');
  const [inputValue, setInputValue] = useState('tester');
  const [advancedOpen, setAdvancedOpen] = useState(false);
  const [shellCommand, setShellCommand] = useState('dumpsys battery | head -20');
  const [shellOutput, setShellOutput] = useState('');
  const [shellBusy, setShellBusy] = useState(false);
  const [apps, setApps] = useState<string[]>([]);
  const [appsBusy, setAppsBusy] = useState(false);
  const [perf, setPerf] = useState<Record<string, unknown> | null>(null);
  const [perfBusy, setPerfBusy] = useState(false);
  const [deviceFile, setDeviceFile] = useState<File | null>(null);
  const [fileBusy, setFileBusy] = useState(false);
  const [leaseBusy, setLeaseBusy] = useState(false);
  const [groups, setGroups] = useState<DeviceGroup[]>([]);
  const [selectedGroup, setSelectedGroup] = useState('');
  const [groupOpen, setGroupOpen] = useState(false);
  const [groupEditing, setGroupEditing] = useState<DeviceGroup | null>(null);
  const [groupForm, setGroupForm] = useState<{ name: string; description: string; deviceIds: string[] }>({
    name: '', description: '', deviceIds: [],
  });
  const [groupSaving, setGroupSaving] = useState(false);
  const [groupError, setGroupError] = useState('');
  const [groupDeleting, setGroupDeleting] = useState<DeviceGroup | null>(null);
  const [groupDeletingBusy, setGroupDeletingBusy] = useState(false);
  const [renameAgent, setRenameAgent] = useState<Agent | null>(null);
  const [renameValue, setRenameValue] = useState('');
  const [renameBusy, setRenameBusy] = useState(false);
  const metricTimer = useRef<number | null>(null);
  const [metrics, setMetrics] = useState<Array<{
    id: string; cpuPercent?: number; memUsedPercent?: number; memUsedKb?: number;
    batteryLevel?: number; batteryTemperature?: number; createdAt: string;
  }>>([]);
  const [metricBusy, setMetricBusy] = useState(false);
  const [recording, setRecording] = useState(false);
  const [recordingBusy, setRecordingBusy] = useState(false);
  const [recordingUrl, setRecordingUrl] = useState('');
  const [baselineFile, setBaselineFile] = useState<File | null>(null);
  const [diffBusy, setDiffBusy] = useState(false);
  const [uia2, setUia2] = useState<{ enabled: boolean; url?: string; message?: string }>({ enabled: false });
  const [uia2Busy, setUia2Busy] = useState(false);
  const [diffResult, setDiffResult] = useState<{
    mismatchPercent: number; diffUrl: string; baselineUrl: string; currentUrl: string;
    baselineWidth: number; baselineHeight: number; currentWidth: number; currentHeight: number;
  } | null>(null);

  const [queueDevice, setQueueDevice] = useState<Device | null>(null);
  const [queueData, setQueueData] = useState<DeviceQueue | null>(null);
  const [queueLoading, setQueueLoading] = useState(false);
  const [queueError, setQueueError] = useState('');
  const queueHandled = useRef<string | null>(null);

  const loadGroups = useCallback(async () => {
    try {
      setGroups(await deviceGroupsApi.list());
    } catch {
      // groups are optional; device list still works
    }
  }, []);

  const load = useCallback(async (silent = false) => {
    if (!silent) setRefreshing(true);
    const [deviceRes, agentRes, groupRes] = await Promise.allSettled([
      uiexecApi.devices(), uiexecApi.agents(), deviceGroupsApi.list(),
    ]);
    if (deviceRes.status === 'fulfilled') {
      setDevices(deviceRes.value.items);
      setDevicesError('');
    } else {
      setDevicesError(safeErrorMessage(deviceRes.reason));
    }
    if (agentRes.status === 'fulfilled') {
      setAgents(agentRes.value);
      setAgentsError('');
    } else {
      setAgentsError(safeErrorMessage(agentRes.reason));
    }
    if (groupRes.status === 'fulfilled') {
      setGroups(groupRes.value);
    }
    setDevicesLoading(false);
    setAgentsLoading(false);
    setRefreshing(false);
    setUpdatedAt(new Date());
  }, []);

  useEffect(() => {
    void load();
    void loadGroups();
  }, [load, loadGroups]);

  usePolling(() => void load(true), POLL_MS);

  const openQueue = useCallback(async (device: Device) => {
    setQueueDevice(device);
    setQueueData(null);
    setQueueError('');
    setQueueLoading(true);
    try {
      const data = await uiexecApi.deviceQueue(device.deviceId);
      setQueueData(data);
    } catch (err) {
      setQueueError(safeErrorMessage(err));
    } finally {
      setQueueLoading(false);
    }
  }, []);

  useEffect(() => {
    const queryDeviceId = route.query.get('queue');
    if (!queryDeviceId) {
      queueHandled.current = null;
      return;
    }
    if (queueHandled.current === queryDeviceId) return;
    const device = devices.find((item) => item.deviceId === queryDeviceId);
    if (device) {
      queueHandled.current = queryDeviceId;
      void openQueue(device);
    }
  }, [route.query, devices, openQueue]);

  const closeQueue = () => {
    setQueueDevice(null);
    setQueueData(null);
    setQueueError('');
    queueHandled.current = null;
    if (route.query.get('queue')) navigate('/devices', { replace: true });
  };

  const streamUrl = (device: Device) => {
    const host = window.location.hostname || '127.0.0.1';
    const secure = window.location.protocol === 'https:';
    const http = secure ? 'https' : 'http';
    const ws = secure ? 'wss' : 'ws';
    const proxy = `${ws}://${host}:8000/?action=proxy-adb&remote=tcp:8886&udid=${encodeURIComponent(device.serial)}`;
    const query = new URLSearchParams({
      action: 'stream', udid: device.serial, player: 'webcodecs', ws: proxy, fitToScreen: 'true',
    });
    return `${http}://${host}:8000/#!${query.toString()}`;
  };

  const loadShot = async (device: Device) => {
    setManageLoading(true);
    setManageError('');
    try {
      const response = await uiexecApi.deviceScreenshot(device.deviceId);
      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      setManageShot((previous) => {
        if (previous) URL.revokeObjectURL(previous);
        return url;
      });
    } catch (err) {
      setManageError(safeErrorMessage(err));
    } finally {
      setManageLoading(false);
    }
  };

  const openManage = (device: Device) => {
    setManageDevice(device);
    setApkFile(null);
    setManageError('');
    setUia2({ enabled: false });
    void uiexecApi.deviceUia2Status(device.deviceId)
      .then((result) => setUia2({ enabled: Boolean(result?.ok), url: result?.uiaUrl }))
      .catch(() => undefined);
    void loadShot(device);
    void uiexecApi.deviceUiTree(device.deviceId)
      .then((tree) => {
        if (tree && tree.width > 0 && tree.height > 0) {
          setDeviceSize({ width: tree.width, height: tree.height });
        }
      })
      .catch(() => undefined);
  };

  const closeManage = () => {
    if (metricTimer.current) { window.clearInterval(metricTimer.current); metricTimer.current = null; }
    setManageDevice(null);
    setManageShot((previous) => {
      if (previous) URL.revokeObjectURL(previous);
      return '';
    });
    setApkFile(null);
    setManageError('');
  };

  const pointerToDevice = (event: ReactPointerEvent<HTMLDivElement>) => {
    const rect = event.currentTarget.getBoundingClientRect();
    const nx = Math.min(1, Math.max(0, (event.clientX - rect.left) / rect.width));
    const ny = Math.min(1, Math.max(0, (event.clientY - rect.top) / rect.height));
    return { x: Math.round(nx * deviceSize.width), y: Math.round(ny * deviceSize.height) };
  };

  const onStreamPointerDown = (event: ReactPointerEvent<HTMLDivElement>) => {
    event.preventDefault();
    event.currentTarget.setPointerCapture(event.pointerId);
    pointerStart.current = { x: event.clientX, y: event.clientY };
  };

  const onStreamPointerUp = (event: ReactPointerEvent<HTMLDivElement>) => {
    event.preventDefault();
    const start = pointerStart.current;
    pointerStart.current = null;
    if (!start || !manageDevice) return;
    const from = pointerToDevice(event);
    const dx = event.clientX - start.x;
    const dy = event.clientY - start.y;
    if (Math.hypot(dx, dy) < 10) {
      void deviceAction({ action: 'tap', from }, `已点击 ${from.x},${from.y}`);
    } else {
      const rect = event.currentTarget.getBoundingClientRect();
      const nx = Math.min(1, Math.max(0, (start.x - rect.left) / rect.width));
      const ny = Math.min(1, Math.max(0, (start.y - rect.top) / rect.height));
      const origin = { x: Math.round(nx * deviceSize.width), y: Math.round(ny * deviceSize.height) };
      void deviceAction({ action: 'swipe', from: origin, to: from, durationMs: 300 }, '已滑动');
    }
  };

  const runShell = async () => {
    if (!manageDevice || !shellCommand.trim()) return;
    setShellBusy(true);
    setManageError('');
    try {
      const result = await uiexecApi.deviceShell(manageDevice.deviceId, shellCommand.trim());
      setShellOutput(`$ ${shellCommand}\n${result.stdout || ''}${result.stderr ? '\n[stderr]\n' + result.stderr : ''}\n[exit ${result.exitCode}]`);
    } catch (err) {
      setManageError(safeErrorMessage(err));
    } finally {
      setShellBusy(false);
    }
  };

  const loadApps = async () => {
    if (!manageDevice) return;
    setAppsBusy(true);
    setManageError('');
    try {
      const result = await uiexecApi.deviceApps(manageDevice.deviceId);
      setApps(result.items || []);
    } catch (err) {
      setManageError(safeErrorMessage(err));
    } finally {
      setAppsBusy(false);
    }
  };

  const uninstallApp = async (packageName: string) => {
    if (!manageDevice) return;
    try {
      const result = await uiexecApi.deviceUninstall(manageDevice.deviceId, packageName);
      if (result && result.ok === false) throw new Error(result.message || '卸载失败');
      toastSuccess(`已卸载 ${packageName}`);
      setApps((prev) => prev.filter((item) => item !== packageName));
    } catch (err) {
      toastError(safeErrorMessage(err));
    }
  };

  const toggleLease = async () => {
    if (!manageDevice) return;
    setLeaseBusy(true);
    setManageError('');
    try {
      if (manageDevice.occupant) {
        await uiexecApi.deviceRelease(manageDevice.deviceId);
        toastSuccess('设备已释放');
      } else {
        await uiexecApi.deviceOccupy(manageDevice.deviceId);
        toastSuccess('设备已占用 10 分钟');
      }
      await load(true);
      setManageDevice(null);
    } catch (err) {
      setManageError(safeErrorMessage(err));
    } finally {
      setLeaseBusy(false);
    }
  };

  const appAction = async (action: 'launch' | 'stop' | 'clear', packageName: string) => {
    if (!manageDevice) return;
    try {
      const result = await uiexecApi.deviceAppAction(manageDevice.deviceId, action, packageName);
      if (result && result.ok === false) throw new Error(result.message || '操作失败');
      toastSuccess(`已${action === 'launch' ? '启动' : action === 'stop' ? '停止' : '清除'} ${packageName}`);
    } catch (err) {
      toastError(safeErrorMessage(err));
    }
  };

  const pushFile = async () => {
    if (!manageDevice || !deviceFile) {
      setManageError('请选择文件');
      return;
    }
    setFileBusy(true);
    setManageError('');
    try {
      const result = await uiexecApi.devicePushFile(manageDevice.deviceId, deviceFile);
      if (result && result.ok === false) throw new Error(result.message || '上传失败');
      toastSuccess(`已上传 ${deviceFile.name}`);
      setDeviceFile(null);
    } catch (err) {
      setManageError(safeErrorMessage(err));
    } finally {
      setFileBusy(false);
    }
  };

  const sampleMetrics = async () => {
    if (!manageDevice) return;
    try {
      const sample = await uiexecApi.devicePerformanceSample(manageDevice.deviceId);
      setMetrics((prev) => [...prev.slice(-59), sample]);
    } catch (err) {
      setManageError(safeErrorMessage(err));
    }
  };

  const startMetricSampling = async () => {
    if (!manageDevice || metricTimer.current) return;
    setMetricBusy(true);
    await sampleMetrics();
    metricTimer.current = window.setInterval(() => void sampleMetrics(), 3000);
    setMetricBusy(false);
  };

  const stopMetricSampling = () => {
    if (metricTimer.current) {
      window.clearInterval(metricTimer.current);
      metricTimer.current = null;
    }
  };

  const startUia2 = async () => {
    if (!manageDevice) return;
    setUia2Busy(true);
    setManageError('');
    try {
      const result = await uiexecApi.deviceUia2Start(manageDevice.deviceId);
      if (result && result.ok === false) throw new Error(result.message || 'UIA2 启动失败');
      setUia2({ enabled: true, url: result?.uiaUrl });
      toastSuccess('UIA2 已启用：中文输入与多点触控可用');
    } catch (err) {
      setManageError(safeErrorMessage(err));
    } finally {
      setUia2Busy(false);
    }
  };

  const stopUia2 = async () => {
    if (!manageDevice) return;
    setUia2Busy(true);
    try {
      await uiexecApi.deviceUia2Stop(manageDevice.deviceId);
      setUia2({ enabled: false });
      toastSuccess('UIA2 已停止，恢复 ADB 驱动');
    } catch (err) {
      setManageError(safeErrorMessage(err));
    } finally {
      setUia2Busy(false);
    }
  };

  const pinch = async (direction: 'in' | 'out') => {
    if (!manageDevice) return;
    const cx = Math.round(deviceSize.width / 2);
    const cy = Math.round(deviceSize.height / 2);
    const spread = direction === 'out' ? 320 : 90;
    const action = {
      action: 'multitouch',
      fingers: [
        { from: { x: cx - 80, y: cy }, to: { x: cx - spread, y: cy }, durationMs: 350 },
        { from: { x: cx + 80, y: cy }, to: { x: cx + spread, y: cy }, durationMs: 350 },
      ],
    };
    await deviceAction(action, direction === 'out' ? '双指放大已发送' : '双指缩小已发送');
  };

  const startRecording = async () => {
    if (!manageDevice) return;
    setRecordingBusy(true);
    setManageError('');
    try {
      const result = await uiexecApi.deviceRecordingStart(manageDevice.deviceId);
      if (result && result.ok === false) throw new Error(result.message || '启动录屏失败');
      setRecording(true);
      toastSuccess('开始录屏（最长 180 秒）');
    } catch (err) {
      setManageError(safeErrorMessage(err));
    } finally {
      setRecordingBusy(false);
    }
  };

  const stopRecording = async () => {
    if (!manageDevice) return;
    setRecordingBusy(true);
    setManageError('');
    try {
      const result = await uiexecApi.deviceRecordingStop(manageDevice.deviceId);
      setRecordingUrl(result.url);
      setRecording(false);
      toastSuccess('录屏已保存');
    } catch (err) {
      setManageError(safeErrorMessage(err));
    } finally {
      setRecordingBusy(false);
    }
  };

  const compareScreenshot = async () => {
    if (!manageDevice || !baselineFile) {
      setManageError('请选择基准截图');
      return;
    }
    setDiffBusy(true);
    setManageError('');
    try {
      setDiffResult(await uiexecApi.deviceCompareScreenshot(manageDevice.deviceId, baselineFile));
    } catch (err) {
      setManageError(safeErrorMessage(err));
    } finally {
      setDiffBusy(false);
    }
  };

  const loadPerformance = async () => {
    if (!manageDevice) return;
    setPerfBusy(true);
    setManageError('');
    try {
      setPerf(await uiexecApi.devicePerformance(manageDevice.deviceId));
    } catch (err) {
      setManageError(safeErrorMessage(err));
    } finally {
      setPerfBusy(false);
    }
  };

  const installApk = async () => {
    if (!manageDevice || !apkFile) {
      setManageError('请选择 APK 文件');
      return;
    }
    setInstallBusy(true);
    setManageError('');
    try {
      const result = await uiexecApi.deviceInstall(manageDevice.deviceId, apkFile);
      if (result && result.ok === false) {
        throw new Error(result.message || '安装失败');
      }
      toastSuccess('APK 已安装');
      setApkFile(null);
    } catch (err) {
      setManageError(safeErrorMessage(err));
    } finally {
      setInstallBusy(false);
    }
  };

  const deviceAction = async (action: Record<string, unknown>, success: string) => {
    if (!manageDevice) return;
    setActionBusy(true);
    setManageError('');
    try {
      const result = await uiexecApi.deviceAction(manageDevice.deviceId, action);
      if (result && result.ok === false) {
        throw new Error(result.message || '操作失败');
      }
      toastSuccess(success);
      await loadShot(manageDevice);
    } catch (err) {
      setManageError(safeErrorMessage(err));
    } finally {
      setActionBusy(false);
    }
  };

  const launchApp = async () => {
    if (!manageDevice || !appPackage.trim()) {
      setManageError('请输入应用包名');
      return;
    }
    setActionBusy(true);
    setManageError('');
    try {
      const result = await uiexecApi.deviceAction(manageDevice.deviceId,
        { action: 'launch', app: appPackage.trim() });
      if (result && result.ok === false) {
        throw new Error(result.message || '启动失败');
      }
      toastSuccess(`已启动 ${appPackage.trim()}`);
      await loadShot(manageDevice);
    } catch (err) {
      setManageError(safeErrorMessage(err));
    } finally {
      setActionBusy(false);
    }
  };

  const refreshQueue = async () => {
    if (!queueDevice) return;
    setQueueLoading(true);
    setQueueError('');
    try {
      setQueueData(await uiexecApi.deviceQueue(queueDevice.deviceId));
    } catch (err) {
      setQueueError(safeErrorMessage(err));
    } finally {
      setQueueLoading(false);
    }
  };

  const toggleAgent = async (agent: Agent) => {
    try {
      await uiexecApi.agentUpdate(agent.agentId, { name: agent.name, enabled: !agent.enabled });
      toastSuccess(agent.enabled ? 'Agent 已停用，其设备不再参与调度' : 'Agent 已启用');
      await load(true);
    } catch (err) {
      toastError(safeErrorMessage(err));
    }
  };

  const submitRenameAgent = async (value: string) => {
    if (!renameAgent || !value.trim()) return;
    setRenameBusy(true);
    try {
      await uiexecApi.agentUpdate(renameAgent.agentId, { name: value.trim() });
      toastSuccess('Agent 名称已更新');
      setRenameAgent(null);
      setRenameValue('');
      await load(true);
    } catch (err) {
      toastError(safeErrorMessage(err));
    } finally {
      setRenameBusy(false);
    }
  };

  const openGroupManager = () => {
    setGroupOpen(true);
    setGroupEditing(null);
    setGroupForm({ name: '', description: '', deviceIds: [] });
    setGroupError('');
  };

  const editGroup = (group: DeviceGroup) => {
    setGroupEditing(group);
    setGroupForm({ name: group.name, description: group.description || '', deviceIds: group.deviceIds });
    setGroupError('');
  };

  const saveGroup = async () => {
    if (!groupForm.name.trim()) {
      setGroupError('分组名称不能为空');
      return;
    }
    setGroupSaving(true);
    setGroupError('');
    try {
      const input = {
        name: groupForm.name.trim(),
        description: groupForm.description.trim() || null,
        deviceIds: groupForm.deviceIds,
      };
      if (groupEditing) await deviceGroupsApi.update(groupEditing.id, input);
      else await deviceGroupsApi.create(input);
      toastSuccess(groupEditing ? '分组已更新' : '分组已创建');
      setGroupEditing(null);
      setGroupForm({ name: '', description: '', deviceIds: [] });
      await loadGroups();
      await load(true);
    } catch (err) {
      setGroupError(safeErrorMessage(err));
    } finally {
      setGroupSaving(false);
    }
  };

  const deleteGroup = async () => {
    if (!groupDeleting) return;
    setGroupDeletingBusy(true);
    try {
      await deviceGroupsApi.remove(groupDeleting.id);
      toastSuccess('分组已删除');
      if (selectedGroup === groupDeleting.id) setSelectedGroup('');
      setGroupDeleting(null);
      await loadGroups();
    } catch (err) {
      toastError(safeErrorMessage(err));
    } finally {
      setGroupDeletingBusy(false);
    }
  };

  const filteredDevices = useMemo(() => {
    const text = keyword.trim().toLowerCase();
    const group = selectedGroup ? groups.find((item) => item.id === selectedGroup) : null;
    return devices.filter((device) => {
      if (group && !group.deviceIds.includes(device.deviceId)) return false;
      if (statusFilter && device.status !== statusFilter) return false;
      if (!text) return true;
      const haystack = [device.serial, device.name, device.model, device.platform, ...(device.tags || [])]
        .filter(Boolean)
        .join(' ')
        .toLowerCase();
      return haystack.includes(text);
    });
  }, [devices, statusFilter, keyword, selectedGroup, groups]);

  const summary = useMemo(() => {
    const total = devices.length;
    const online = devices.filter((device) => device.online).length;
    const idle = devices.filter((device) => device.status === 'IDLE').length;
    const busy = devices.filter(
      (device) => device.status === 'SCRIPT_RUNNING' || device.status === 'AI_RUNNING' || device.status === 'QUEUED',
    ).length;
    const offline = devices.filter((device) => device.status === 'OFFLINE').length;
    return { total, online, idle, busy, offline };
  }, [devices]);

  return (
    <div className="page-stack">
      <PageHeading
        title="设备池"
        description="查看 Agent 同步的设备状态、FIFO 队列与当前任务；每 10 秒自动刷新。"
        actions={
          <div className="page-actions">
            <span className="heading-status">
              <span className={cx('live-dot', refreshing && 'is-active')} />
              {updatedAt ? `更新于 ${formatDateTime(updatedAt)}` : '加载中'}
            </span>
            <Button variant="secondary" icon="refresh" loading={refreshing} onClick={() => void load(true)}>
              刷新
            </Button>
          </div>
        }
      />

      <div className="device-summary-row">
        <div className="summary-chip">
          <span className="summary-chip-label">设备总数</span>
          <span className="summary-chip-value">{summary.total}</span>
        </div>
        <div className="summary-chip">
          <span className="summary-chip-label">在线</span>
          <span className="summary-chip-value text-success">{summary.online}</span>
        </div>
        <div className="summary-chip">
          <span className="summary-chip-label">空闲</span>
          <span className="summary-chip-value text-success">{summary.idle}</span>
        </div>
        <div className="summary-chip">
          <span className="summary-chip-label">执行 / 排队</span>
          <span className="summary-chip-value text-info">{summary.busy}</span>
        </div>
        <div className="summary-chip">
          <span className="summary-chip-label">离线</span>
          <span className="summary-chip-value text-muted">{summary.offline}</span>
        </div>
      </div>

      <div className="group-filter-row">
        <button type="button" className={cx('group-chip', !selectedGroup && 'is-active')} onClick={() => setSelectedGroup('')}>
          全部设备 <span>{devices.length}</span>
        </button>
        {groups.map((group) => (
          <button key={group.id} type="button"
                  className={cx('group-chip', selectedGroup === group.id && 'is-active')}
                  onClick={() => setSelectedGroup(group.id)}>
            {group.name} <span>{group.deviceCount}</span>
          </button>
        ))}
        <Button size="sm" variant="ghost" onClick={openGroupManager}>分组管理</Button>
      </div>

      <Card>
        <CardHead
          title="设备列表"
          description="设备状态由 Agent 心跳与任务状态机共同维护"
          icon="devices"
          actions={
            <div className="filter-inline">
              <Select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}>
                {DEVICE_FILTERS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </Select>
              <Input
                icon="search"
                placeholder="serial / 名称 / 标签"
                value={keyword}
                onChange={(event) => setKeyword(event.target.value)}
              />
            </div>
          }
        />
        <div className="card-body">
          {devicesError ? (
            <ErrorPanel
              message={devicesError}
              onRetry={() => void load()}
              hint="请确认 ui-execution（默认 8081）服务已启动。"
            />
          ) : devicesLoading ? (
            <div className="device-card-grid">
              {Array.from({ length: 3 }).map((_, index) => (
                <div className="device-card" key={index}>
                  <span className="skeleton sk-w-70" />
                  <span className="skeleton sk-w-40" />
                  <span className="skeleton sk-w-40" />
                </div>
              ))}
            </div>
          ) : filteredDevices.length === 0 ? (
            <EmptyState
              icon="devices"
              title={devices.length === 0 ? '还没有注册设备' : '没有匹配的设备'}
              description={
                devices.length === 0
                  ? '启动 device-agent 并完成注册后，设备会自动同步到平台'
                  : '尝试调整状态筛选或关键字'
              }
              action={
                devices.length > 0 ? (
                  <Button
                    size="sm"
                    variant="secondary"
                    onClick={() => {
                      setStatusFilter('');
                      setKeyword('');
                    }}
                  >
                    清空筛选
                  </Button>
                ) : undefined
              }
            />
          ) : (
            <div className="device-card-grid">
              {filteredDevices.map((device) => {
                const meta = metaOf(DEVICE_STATUS_META, device.status);
                return (
                  <article className="device-card" key={device.deviceId}>
                    <span className={cx('device-status-line', `tone-bg-${meta.tone}`)} />
                    <div className="device-card-head">
                      <div>
                        <div className="device-serial mono">{device.serial}</div>
                        <div className="device-name">
                          {device.name || device.model || '未命名设备'}
                          {device.name && device.model ? ` · ${device.model}` : ''}
                          {device.platform ? <span className="device-platform"> · {device.platform}</span> : null}
                        </div>
                      </div>
                      <StatusPill meta={meta} />
                    </div>

                    <div className="tag-row">
                      {(device.tags || []).length > 0 ? (
                        (device.tags || []).slice(0, 5).map((tag) => (
                          <Pill tone="gray" key={tag}>
                            {tag}
                          </Pill>
                        ))
                      ) : (
                        <span className="text-muted">无标签</span>
                      )}
                      {(device.tags || []).length > 5 ? <Pill tone="gray">+{(device.tags || []).length - 5}</Pill> : null}
                    </div>

                    <div className="device-info-grid">
                      <div>
                        <span className="meta-label">队列长度</span>
                        <strong className={device.queueLength ? 'text-info' : ''}>{device.queueLength ?? 0}</strong>
                      </div>
                      <div>
                        <span className="meta-label">所属 Agent</span>
                        <strong title={device.agentId || ''}>
                          {device.agentName || shortId(device.agentId)}
                        </strong>
                      </div>
                      <div>
                        <span className="meta-label">最近心跳</span>
                        <strong>{formatDateTime(device.lastSeenAt)}</strong>
                      </div>
                      <div>
                        <span className="meta-label">占用</span>
                        <strong className={device.occupant ? 'text-warning' : ''}>{device.occupant || '空闲'}</strong>
                      </div>
                      <div>
                        <span className="meta-label">在线</span>
                        <strong className={device.online ? 'text-success' : 'text-muted'}>
                          {device.online ? '是' : '否'}
                        </strong>
                      </div>
                    </div>

                    <div className="device-card-foot">
                      {device.currentTaskId ? (
                        <button
                          type="button"
                          className="device-current-task"
                          onClick={() => navigate(`/tasks/${device.currentTaskId}`)}
                          title={device.currentTaskId}
                        >
                          <Icon name="terminal" size={14} />
                          当前任务 {shortId(device.currentTaskId)}
                          <Icon name="arrow-right" size={12} />
                        </button>
                      ) : (
                        <span className="device-idle-text">
                          <Icon name="check-circle" size={14} />
                          无运行任务
                        </span>
                      )}
                      <div className="row-actions">
                        <Button size="sm" variant="ghost" icon="server" onClick={() => openManage(device)}>
                          管理
                        </Button>
                        <Button size="sm" variant="secondary" icon="queue" onClick={() => void openQueue(device)}>
                          查看队列
                        </Button>
                      </div>
                    </div>
                  </article>
                );
              })}
            </div>
          )}
        </div>
      </Card>

      <Modal
        open={!!manageDevice}
        title="真机管理"
        description={manageDevice ? `${manageDevice.serial} · ${manageDevice.name || manageDevice.model || ''}` : ''}
        onClose={closeManage}
        size="lg"
        footer={
          <>
            <Button variant="secondary" onClick={closeManage}>关闭</Button>
            <Button variant="secondary" icon="refresh" loading={manageLoading} onClick={() => manageDevice && void loadShot(manageDevice)}>
              刷新截图
            </Button>
          </>
        }
      >
        <div className="form-stack">
          {manageError ? <ErrorPanel message={manageError} compact /> : null}
          {manageDevice ? (
            <div className="scrcpy-stage">
              <iframe
                title="scrcpy-stream"
                src={streamUrl(manageDevice)}
                className="scrcpy-frame"
                allow="autoplay"
              />
              <div
                className="scrcpy-overlay"
                onPointerDown={onStreamPointerDown}
                onPointerUp={onStreamPointerUp}
                onContextMenu={(event) => event.preventDefault()}
              />
              <div className="scrcpy-hint">实时画面（scrcpy/WebCodecs）· 鼠标点击/拖动会下发到真机；无画面请运行 make scrcpy-up</div>
            </div>
          ) : null}
          <div style={{ background: '#0b1220', borderRadius: 10, padding: 8, minHeight: 160, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            {manageLoading && !manageShot ? (
              <span className="text-muted">正在获取截图…</span>
            ) : manageShot ? (
              <img src={manageShot} alt="device screenshot" style={{ maxWidth: '100%', maxHeight: 420, borderRadius: 6 }} />
            ) : (
              <span className="text-muted">暂无截图（设备可能离线或锁屏）</span>
            )}
          </div>
          <div className="form-grid-2">
            <div className="form-stack">
              <span className="meta-label">快捷操作</span>
              <div className="row-actions">
                <Button size="sm" variant="secondary" loading={actionBusy}
                        onClick={() => void deviceAction({ action: 'back' }, '已返回')}>
                  返回
                </Button>
                <Button size="sm" variant="secondary" loading={actionBusy}
                        onClick={() => void deviceAction({ action: 'home' }, '已回到桌面')}>
                  Home
                </Button>
                <Button size="sm" variant="ghost" loading={actionBusy}
                        onClick={() => void uiexecApi.deviceRotate(manageDevice!.deviceId, 'left').then(() => toastSuccess('已左转')).catch((err) => setManageError(safeErrorMessage(err)))}>
                  左转
                </Button>
                <Button size="sm" variant="ghost" loading={actionBusy}
                        onClick={() => void uiexecApi.deviceRotate(manageDevice!.deviceId, 'right').then(() => toastSuccess('已右转')).catch((err) => setManageError(safeErrorMessage(err)))}>
                  右转
                </Button>
              </div>
              <div className="form-grid-2">
                <Input value={tapX} onChange={(event) => setTapX(event.target.value)} placeholder="x" />
                <Input value={tapY} onChange={(event) => setTapY(event.target.value)} placeholder="y" />
              </div>
              <Button size="sm" variant="secondary" loading={actionBusy}
                      onClick={() => void deviceAction(
                        { action: 'tap', from: { x: Number(tapX), y: Number(tapY) } },
                        `已点击 ${tapX},${tapY}`)}>
                点击坐标
              </Button>
              <div className="form-grid-2">
                <Input value={inputTarget} onChange={(event) => setInputTarget(event.target.value)}
                       placeholder="目标文本" />
                <Input value={inputValue} onChange={(event) => setInputValue(event.target.value)}
                       placeholder="输入内容" />
              </div>
              <Button size="sm" variant="secondary" loading={actionBusy}
                      onClick={() => void deviceAction(
                        { action: 'input', target: { text: inputTarget }, text: inputValue },
                        '输入已发送')}>
                输入文本
              </Button>
              <Button size="sm" variant="ghost" loading={actionBusy}
                      onClick={() => void deviceAction(
                        { action: 'text', text: inputValue },
                        '文本已发送到焦点')}>
                发送到当前焦点
              </Button>
            </div>
            <div className="form-stack">
              <Button size="sm" variant="ghost" onClick={() => setAdvancedOpen((prev) => !prev)}>
                {advancedOpen ? '收起高级工具' : '高级工具（Shell / 应用 / 性能）'}
              </Button>
              {advancedOpen ? (
                <div className="form-stack">
                  <span className="meta-label">UIA2（中文输入 / 多点触控）</span>
                  <div className="row-actions">
                    <Pill tone={uia2.enabled ? 'green' : 'gray'} dot>{uia2.enabled ? 'UIA2 已启用' : 'ADB 模式'}</Pill>
                    {!uia2.enabled ? (
                      <Button size="sm" variant="secondary" loading={uia2Busy} onClick={() => void startUia2()}>启用 UIA2</Button>
                    ) : (
                      <Button size="sm" variant="ghost" loading={uia2Busy} onClick={() => void stopUia2()}>关闭 UIA2</Button>
                    )}
                    <Button size="sm" variant="ghost" disabled={!uia2.enabled} onClick={() => void pinch('out')}>双指放大</Button>
                    <Button size="sm" variant="ghost" disabled={!uia2.enabled} onClick={() => void pinch('in')}>双指缩小</Button>
                  </div>
                  {uia2.url ? <div className="field-hint mono">UIA2: {uia2.url}</div> : null}
                  <span className="meta-label">远程 Shell</span>
                  <Input value={shellCommand} onChange={(event) => setShellCommand(event.target.value)} />
                  <Button size="sm" variant="secondary" loading={shellBusy} onClick={() => void runShell()}>执行命令</Button>
                  {shellOutput ? <pre className="shell-output">{shellOutput}</pre> : null}
                  <span className="meta-label">应用管理</span>
                  <Button size="sm" variant="secondary" loading={appsBusy} onClick={() => void loadApps()}>加载应用列表</Button>
                  {apps.length > 0 ? (
                    <div className="app-list">
                      {apps.map((pkg) => (
                        <div key={pkg} className="app-row">
                          <span className="mono">{pkg}</span>
                          <div className="row-actions">
                            <Button size="sm" variant="ghost" onClick={() => void appAction('launch', pkg)}>启动</Button>
                            <Button size="sm" variant="ghost" onClick={() => void appAction('stop', pkg)}>停止</Button>
                            <Button size="sm" variant="ghost" onClick={() => void appAction('clear', pkg)}>清除</Button>
                            <Button size="sm" variant="ghost" onClick={() => void uninstallApp(pkg)}>卸载</Button>
                          </div>
                        </div>
                      ))}
                    </div>
                  ) : null}
                  <span className="meta-label">性能</span>
                  <Button size="sm" variant="secondary" loading={perfBusy} onClick={() => void loadPerformance()}>读取性能</Button>
                  {perf ? <pre className="shell-output">{JSON.stringify(perf, null, 2)}</pre> : null}
                  <span className="meta-label">性能曲线</span>
                  <div className="row-actions">
                    <Button size="sm" variant="secondary" loading={metricBusy} onClick={() => void startMetricSampling()}>
                      {metricTimer.current ? '采样中…' : '开始采样'}
                    </Button>
                    <Button size="sm" variant="ghost" onClick={stopMetricSampling}>停止采样</Button>
                    <span className="field-hint">每 3 秒采样一次（保留最近 60 点）</span>
                  </div>
                  <MetricChart samples={metrics} />
                  <span className="meta-label">录屏</span>
                  <div className="row-actions">
                    {!recording ? (
                      <Button size="sm" variant="secondary" loading={recordingBusy} onClick={() => void startRecording()}>开始录屏</Button>
                    ) : (
                      <Button size="sm" variant="danger" loading={recordingBusy} onClick={() => void stopRecording()}>停止并保存</Button>
                    )}
                    {recordingUrl ? <a href={recordingUrl} target="_blank" rel="noreferrer">查看录屏</a> : null}
                  </div>
                  <span className="meta-label">截图对比</span>
                  <input type="file" accept="image/png,image/jpeg" onChange={(event) => setBaselineFile(event.target.files?.[0] || null)} />
                  <Button size="sm" variant="secondary" loading={diffBusy} onClick={() => void compareScreenshot()}>
                    与当前屏幕对比{baselineFile ? `（${baselineFile.name}）` : ''}
                  </Button>
                  {diffResult ? (
                    <div className="diff-result">
                      <div className="diff-summary">差异像素：<strong>{diffResult.mismatchPercent}%</strong>
                        <span className="text-muted">（基准 {diffResult.baselineWidth}×{diffResult.baselineHeight} / 当前 {diffResult.currentWidth}×{diffResult.currentHeight}）</span>
                      </div>
                      <div className="diff-images">
                        <img src={diffResult.baselineUrl} alt="基准" />
                        <img src={diffResult.currentUrl} alt="当前" />
                        <img src={diffResult.diffUrl} alt="差异" />
                      </div>
                    </div>
                  ) : null}
                </div>
              ) : null}
              <span className="meta-label">上传文件到 /sdcard/Download</span>
              <input type="file" onChange={(event) => setDeviceFile(event.target.files?.[0] || null)} />
              <Button size="sm" variant="secondary" loading={fileBusy} onClick={() => void pushFile()}>
                上传{deviceFile ? ` ${deviceFile.name}` : ''}
              </Button>
              <span className="meta-label">安装 APK</span>
              <input
                type="file"
                accept=".apk,application/vnd.android.package-archive"
                onChange={(event) => setApkFile(event.target.files?.[0] || null)}
              />
              <Button variant="secondary" loading={installBusy} onClick={() => void installApk()}>
                安装{apkFile ? ` ${apkFile.name}` : ''}
              </Button>
            </div>
            <div className="form-stack">
              <span className="meta-label">启动应用</span>
              <Input
                value={appPackage}
                placeholder="com.testhub.demo"
                onChange={(event) => setAppPackage(event.target.value)}
              />
              <Button variant="secondary" loading={actionBusy} onClick={() => void launchApp()}>
                启动应用
              </Button>
            </div>
          </div>
          <div className="field-hint">安装与启动走设备所属 Agent（需开启 USB 安装 / 安全设置）。</div>
        </div>
      </Modal>

      <Card>
        <CardHead
          title="Agent 列表"
          description="设备子服务器注册与心跳状态"
          icon="server"
          actions={
            <Button
              size="sm"
              variant="ghost"
              icon="refresh"
              loading={refreshing}
              onClick={() => void load(true)}
            >
              刷新
            </Button>
          }
        />
        <div className="card-body no-pad">
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>名称</th>
                  <th>host</th>
                  <th>baseUrl</th>
                  <th>状态</th>
                  <th>最后心跳</th>
                  <th>设备数</th>
                  <th className="th-right">操作</th>
                </tr>
              </thead>
              <tbody>
                {agentsLoading ? (
                  <SkeletonRows rows={3} cols={7} />
                ) : agentsError ? (
                  <tr>
                    <td colSpan={7}>
                      <ErrorPanel message={agentsError} compact onRetry={() => void load()} />
                    </td>
                  </tr>
                ) : agents.length === 0 ? (
                  <tr>
                    <td colSpan={7}>
                      <EmptyState
                        compact
                        icon="server"
                        title="还没有注册 Agent"
                        description="启动 device-agent 后，会自动注册并周期心跳"
                      />
                    </td>
                  </tr>
                ) : (
                  agents.map((agent) => {
                    const statusMeta = metaOf(AGENT_STATUS_META, agent.status);
                    return (
                      <tr key={agent.agentId}>
                        <td>
                          <div className="cell-title">{agent.name}</div>
                          <div className="cell-sub mono">{agent.agentId}</div>
                        </td>
                        <td className="mono">{agent.host || '—'}</td>
                        <td className="mono">{truncate(agent.baseUrl, 40) || '—'}</td>
                        <td>
                          <StatusPill meta={statusMeta} />
                        </td>
                        <td className="text-muted">{formatDateTime(agent.lastHeartbeat)}</td>
                        <td>
                          <Pill tone="blue">{agent.devices?.length ?? 0} 台</Pill>
                        </td>
                        <td>
                          <div className="row-actions">
                            <Button size="sm" variant="ghost"
                                    onClick={() => { setRenameAgent(agent); setRenameValue(agent.name); }}>
                              重命名
                            </Button>
                            <Button size="sm" variant="ghost" onClick={() => void toggleAgent(agent)}>
                              {agent.enabled === false ? '启用' : '停用'}
                            </Button>
                          </div>
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        </div>
      </Card>

      <PromptDialog
        open={!!renameAgent}
        title="重命名 Agent"
        message={renameAgent ? `Agent ${renameAgent.agentId}` : null}
        label="名称"
        placeholder="如：实验室小米机架"
        required
        confirmText="保存"
        loading={renameBusy}
        onConfirm={(value) => void submitRenameAgent(value)}
        onClose={() => { setRenameAgent(null); setRenameValue(''); }}
      />

      <Modal
        open={groupOpen}
        title="设备分组"
        description="分组用于快速筛选设备；一个设备可以属于多个分组。"
        onClose={() => { if (!groupSaving) setGroupOpen(false); }}
        size="md"
        closeOnOverlay={!groupSaving}
        footer={<Button variant="secondary" onClick={() => setGroupOpen(false)} disabled={groupSaving}>关闭</Button>}
      >
        <div className="form-stack">
          {groupError ? <ErrorPanel message={groupError} onRetry={() => setGroupError('')} /> : null}
          <div className="group-manager-list">
            {groups.length === 0 ? <span className="text-muted">暂无分组</span> : groups.map((group) => (
              <div key={group.id} className="group-manager-row">
                <span>{group.name}</span>
                <span className="text-muted">{group.deviceCount} 台</span>
                <div className="row-actions">
                  <Button size="sm" variant="ghost" onClick={() => editGroup(group)}>编辑</Button>
                  <Button size="sm" variant="ghost" onClick={() => setGroupDeleting(group)}>删除</Button>
                </div>
              </div>
            ))}
          </div>
          <div className="divider" />
          <Field label={groupEditing ? `编辑分组：${groupEditing.name}` : '新建分组'} htmlFor="group-name">
            <Input id="group-name" value={groupForm.name} placeholder="如：小米真机"
                   onChange={(event) => setGroupForm((prev) => ({ ...prev, name: event.target.value }))} />
          </Field>
          <Field label="描述" htmlFor="group-desc">
            <Input id="group-desc" value={groupForm.description}
                   onChange={(event) => setGroupForm((prev) => ({ ...prev, description: event.target.value }))} />
          </Field>
          <Field label="包含设备" htmlFor="group-devices">
            <div className="group-device-picker">
              {devices.map((device) => (
                <label key={device.deviceId} className="checkbox-row">
                  <input
                    type="checkbox"
                    checked={groupForm.deviceIds.includes(device.deviceId)}
                    onChange={(event) => setGroupForm((prev) => ({
                      ...prev,
                      deviceIds: event.target.checked
                        ? [...prev.deviceIds, device.deviceId]
                        : prev.deviceIds.filter((id) => id !== device.deviceId),
                    }))}
                  />
                  <span className="mono">{device.serial}</span>
                  <span className="text-muted">{device.name || device.model}</span>
                </label>
              ))}
            </div>
          </Field>
          <div className="row-actions">
            <Button variant="primary" loading={groupSaving} onClick={() => void saveGroup()}>
              {groupEditing ? '保存分组' : '创建分组'}
            </Button>
            {groupEditing ? <Button variant="ghost" onClick={() => { setGroupEditing(null); setGroupForm({ name: '', description: '', deviceIds: [] }); }}>取消编辑</Button> : null}
          </div>
        </div>
      </Modal>

      <ConfirmDialog
        open={!!groupDeleting}
        title="删除设备分组"
        message={`确认删除“${groupDeleting?.name || ''}”？设备本身不会被删除。`}
        confirmText="删除"
        tone="danger"
        loading={groupDeletingBusy}
        onConfirm={() => void deleteGroup()}
        onClose={() => setGroupDeleting(null)}
      />

      <Modal
        open={Boolean(queueDevice)}
        title="设备 FIFO 队列"
        description={
          queueDevice
            ? `${queueDevice.serial} · ${queueDevice.name || queueDevice.model || '未命名设备'}`
            : undefined
        }
        onClose={closeQueue}
        size="lg"
        footer={
          <>
            <Button variant="secondary" onClick={closeQueue}>
              关闭
            </Button>
            <Button variant="primary" icon="refresh" loading={queueLoading} onClick={() => void refreshQueue()}>
              刷新队列
            </Button>
          </>
        }
      >
        {queueError ? (
          <ErrorPanel message={queueError} onRetry={() => void refreshQueue()} />
        ) : queueLoading ? (
          <LoadingPanel text="正在加载设备队列…" />
        ) : queueData ? (
          <div className="form-stack">
            <div className="queue-summary">
              <Pill tone="blue">队列长度 {queueData.queueLength}</Pill>
              {queueData.serial ? <span className="mono">{queueData.serial}</span> : null}
              <span className="text-muted">队列按 FIFO 顺序执行，设备空闲时自动出队下一个任务。</span>
            </div>
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>位置</th>
                    <th>用例</th>
                    <th>状态</th>
                    <th>执行器</th>
                    <th>创建时间</th>
                    <th className="th-right">操作</th>
                  </tr>
                </thead>
                <tbody>
                  {(queueData.tasks || []).length === 0 ? (
                    <tr>
                      <td colSpan={6}>
                        <EmptyState
                          compact
                          icon="check-circle"
                          title="队列为空"
                          description="设备空闲，可以立即承接新任务"
                        />
                      </td>
                    </tr>
                  ) : (
                    (queueData.tasks || [])
                      .slice()
                      .sort((a, b) => a.position - b.position)
                      .map((task) => (
                        <tr key={task.taskId}>
                          <td>
                            <span className="queue-position">#{task.position}</span>
                          </td>
                          <td>
                            <div className="cell-title sm">{task.caseTitle || '—'}</div>
                            <div className="cell-sub mono">{task.taskId}</div>
                          </td>
                          <td>
                            <StatusPill meta={metaOf(TASK_STATUS_META, task.status)} />
                          </td>
                          <td>
                            <StatusPill meta={metaOf(EXECUTOR_META, task.executorType)} dot={false} />
                          </td>
                          <td className="text-muted">{formatDateTime(task.createdAt)}</td>
                          <td className="td-right">
                            <button
                              type="button"
                              className="link-btn"
                              onClick={() => {
                                closeQueue();
                                navigate(`/tasks/${task.taskId}`);
                              }}
                            >
                              轨迹
                              <Icon name="arrow-right" size={13} />
                            </button>
                          </td>
                        </tr>
                      ))
                  )}
                </tbody>
              </table>
            </div>
          </div>
        ) : null}
      </Modal>

      <div className="dashboard-footnote">
        <Icon name="info" size={14} />
        V1 不提供设备投屏；模拟设备由 device-agent 提供截图，任务截图请进入任务轨迹查看。
        {devices.length > 0 ? ` 当前共 ${devices.length} 台设备。` : ''}
      </div>

    </div>
  );
}
