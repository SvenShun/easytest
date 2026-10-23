package com.testhub.uiexec.service;

import com.testhub.common.security.CurrentUser;
import com.testhub.common.util.Ids;
import com.testhub.common.web.PageResponse;
import com.testhub.uiexec.domain.AuditLogEntity;
import com.testhub.uiexec.repository.AuditLogRepository;
import com.testhub.uiexec.web.dto.StatsDtos.AuditLogView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/** Append-only audit trail for skills pointer changes and other admin actions. */
@Service
public class AuditService {

    private static final int MAX_PAGE_SIZE = 200;

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void record(String actor, String action, String target, String detail) {
        String effectiveActor = actor;
        if (effectiveActor == null || effectiveActor.isBlank()) {
            var principal = CurrentUser.get();
            effectiveActor = principal == null ? "system" : principal.username();
        }
        repository.save(new AuditLogEntity(Ids.newId("audit"), effectiveActor, truncate(action, 100),
                truncate(target, 500), truncate(detail, 2000), Instant.now()));
    }

    public PageResponse<AuditLogView> list(int page, int size, String action) {
        int pageNumber = Math.max(1, page);
        int pageSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        PageRequest request = PageRequest.of(pageNumber - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuditLogEntity> result = action == null || action.isBlank()
                ? repository.findAll(request)
                : repository.findByAction(action.trim(), request);
        List<AuditLogView> items = result.getContent().stream()
                .map(entity -> new AuditLogView(entity.getId(), entity.getActor(), entity.getAction(),
                        entity.getTarget(), entity.getDetail(), entity.getCreatedAt()))
                .toList();
        return PageResponse.of(items, result.getTotalElements(), pageNumber, pageSize);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
