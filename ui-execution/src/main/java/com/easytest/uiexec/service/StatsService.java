package com.easytest.uiexec.service;

import com.easytest.uiexec.domain.DeviceEntity;
import com.easytest.uiexec.domain.DeviceStatus;
import com.easytest.uiexec.domain.SkillsPointerEntity;
import com.easytest.uiexec.domain.TaskStatus;
import com.easytest.uiexec.repository.DeviceRepository;
import com.easytest.uiexec.repository.FailureReportRepository;
import com.easytest.uiexec.repository.TaskRepository;
import com.easytest.uiexec.web.dto.StatsDtos.DeviceStats;
import com.easytest.uiexec.web.dto.StatsDtos.FailureCount;
import com.easytest.uiexec.web.dto.StatsDtos.SkillsStats;
import com.easytest.uiexec.web.dto.StatsDtos.StatsView;
import com.easytest.uiexec.web.dto.StatsDtos.TaskStats;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** Aggregated dashboard statistics ({@code GET /stats}). */
@Service
public class StatsService {

    private final DeviceRepository devices;
    private final DeviceService deviceService;
    private final TaskRepository tasks;
    private final FailureReportRepository failureReports;
    private final SkillsService skills;

    public StatsService(DeviceRepository devices, DeviceService deviceService, TaskRepository tasks,
                        FailureReportRepository failureReports, SkillsService skills) {
        this.devices = devices;
        this.deviceService = deviceService;
        this.tasks = tasks;
        this.failureReports = failureReports;
        this.skills = skills;
    }

    public StatsView stats() {
        List<DeviceEntity> allDevices = devices.findAll();
        long online = 0;
        long idle = 0;
        long running = 0;
        long offline = 0;
        for (DeviceEntity device : allDevices) {
            DeviceStatus status = deviceService.statusOf(device);
            if (status == DeviceStatus.OFFLINE) {
                offline++;
            } else {
                online++;
            }
            if (status == DeviceStatus.IDLE) {
                idle++;
            }
            if (status == DeviceStatus.SCRIPT_RUNNING || status == DeviceStatus.AI_RUNNING) {
                running++;
            }
        }
        DeviceStats deviceStats = new DeviceStats(allDevices.size(), online, idle, running, offline);

        TaskStats taskStats = new TaskStats(
                tasks.countByStatus(TaskStatus.PENDING),
                tasks.countByStatusIn(List.of(TaskStatus.DISPATCHED, TaskStatus.RUNNING)),
                tasks.countByStatus(TaskStatus.SUCCESS),
                tasks.countByStatus(TaskStatus.FAILED),
                tasks.countByStatus(TaskStatus.TIMEOUT),
                tasks.countByStatus(TaskStatus.CANCELLED));

        List<FailureCount> failureCounts = new ArrayList<>();
        for (FailureReportRepository.CategoryCount count : failureReports.countByCategory()) {
            failureCounts.add(new FailureCount(count.getCategory().name(), count.getCount()));
        }

        SkillsPointerEntity pointer = skills.getOrCreate();
        SkillsStats skillsStats = new SkillsStats(skills.configured(pointer), pointer.getActiveCommit());
        return new StatsView(deviceStats, taskStats, failureCounts, skillsStats);
    }
}
