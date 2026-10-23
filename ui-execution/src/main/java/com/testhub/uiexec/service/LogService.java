package com.testhub.uiexec.service;

import com.testhub.common.util.Ids;
import com.testhub.common.web.ApiException;
import com.testhub.uiexec.domain.LogEntity;
import com.testhub.uiexec.repository.LogRepository;
import com.testhub.uiexec.web.dto.TaskDtos.LogLine;
import com.testhub.uiexec.web.dto.TaskDtos.LogView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Append-only task logs; {@code seq} increases monotonically per task. */
@Service
public class LogService {

    private static final Logger log = LoggerFactory.getLogger(LogService.class);
    private static final int DEFAULT_LIMIT = 500;

    private final LogRepository repository;
    /** Serialises seq allocation + writes so concurrent callbacks cannot collide. */
    private final Object seqMonitor = new Object();

    public LogService(LogRepository repository) {
        this.repository = repository;
    }

    public void append(String taskId, List<LogLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        if (lines.size() > 1000) {
            throw ApiException.badRequest("单次日志批量不能超过 1000 条");
        }
        synchronized (seqMonitor) {
            int seq = repository.maxSeq(taskId);
            List<LogEntity> batch = new ArrayList<>();
            Instant now = Instant.now();
            for (LogLine line : lines) {
                if (line == null) {
                    continue;
                }
                String level = line.level() == null || line.level().isBlank()
                        ? "INFO" : truncate(line.level().trim().toUpperCase(), 20);
                String message = line.message() == null ? "" : truncate(line.message(), 4000);
                seq++;
                batch.add(new LogEntity(Ids.newId("log"), taskId, seq, level, message, now));
            }
            repository.saveAll(batch);
        }
        log.debug("追加任务日志 taskId={} lines={}", taskId, lines.size());
    }

    public List<LogView> recent(String taskId, int limit) {
        int size = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, DEFAULT_LIMIT);
        List<LogEntity> entities = repository.findByTaskIdOrderBySeqDesc(taskId, PageRequest.of(0, size));
        List<LogEntity> ascending = new ArrayList<>(entities);
        Collections.reverse(ascending);
        return ascending.stream().map(this::toView).toList();
    }

    public List<LogView> afterSeq(String taskId, int afterSeq, int limit) {
        int size = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, DEFAULT_LIMIT);
        return repository.findByTaskIdAndSeqGreaterThanOrderBySeqAsc(taskId, Math.max(0, afterSeq),
                        PageRequest.of(0, size)).stream()
                .map(this::toView)
                .toList();
    }

    private LogView toView(LogEntity entity) {
        return new LogView(entity.getSeq(), entity.getLevel(), entity.getMessage(), entity.getCreatedAt());
    }

    private String truncate(String value, int max) {
        return value == null ? null : value.length() <= max ? value : value.substring(0, max);
    }
}
