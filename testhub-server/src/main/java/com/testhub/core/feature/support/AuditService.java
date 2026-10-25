package com.testhub.core.feature.support;

import com.testhub.common.security.AuthPrincipal;
import com.testhub.common.security.CurrentUser;
import com.testhub.common.util.Ids;
import org.springframework.stereotype.Service;

import java.time.Instant;

/** Central audit writer used by the mutable admin operations. */
@Service
public class AuditService {

    public static final String MODEL_CONFIG_UPDATE = "MODEL_CONFIG_UPDATE";
    public static final String CASE_REVIEW = "CASE_REVIEW";
    public static final String PLAN_TRIGGER = "PLAN_TRIGGER";
    public static final String SCRIPT_UPLOAD = "SCRIPT_UPLOAD";
    public static final String USER_CREATE = "USER_CREATE";
    public static final String USER_UPDATE = "USER_UPDATE";
    public static final String USER_RESET_PASSWORD = "USER_RESET_PASSWORD";

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void record(String action, String target, String detail) {
        record(currentActor(), action, target, detail);
    }

    public void record(String actor, String action, String target, String detail) {
        repository.save(new AuditLog(Ids.newId("audit"), actor, action, target, detail, Instant.now()));
    }

    private String currentActor() {
        AuthPrincipal principal = CurrentUser.get();
        return principal == null ? "system" : principal.username();
    }
}
