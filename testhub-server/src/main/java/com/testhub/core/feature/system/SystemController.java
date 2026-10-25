package com.testhub.core.feature.system;

import com.testhub.common.security.RequireRole;
import com.testhub.common.security.Role;
import com.testhub.common.web.PageResponse;
import com.testhub.core.feature.auth.AppUserRepository;
import com.testhub.core.feature.auth.UserView;
import com.testhub.core.feature.support.AuditLogRepository;
import com.testhub.core.feature.support.AuditLogView;
import com.testhub.core.feature.support.PageSupport;
import com.testhub.core.feature.system.UserAdminDtos.CreateUserRequest;
import com.testhub.core.feature.system.UserAdminDtos.ResetPasswordRequest;
import com.testhub.core.feature.system.UserAdminDtos.UpdateUserRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** System settings: users, model configurations and audit trail. */
@RestController
@RequestMapping("/api/v1/system")
@RequireRole(Role.ADMIN)
public class SystemController {

    private final AppUserRepository userRepository;
    private final ModelConfigService modelConfigService;
    private final AuditLogRepository auditLogRepository;
    private final UserAdminService userAdminService;

    public SystemController(AppUserRepository userRepository, ModelConfigService modelConfigService,
                            AuditLogRepository auditLogRepository, UserAdminService userAdminService) {
        this.userRepository = userRepository;
        this.modelConfigService = modelConfigService;
        this.auditLogRepository = auditLogRepository;
        this.userAdminService = userAdminService;
    }

    @GetMapping("/users")
    public PageResponse<UserView> users(@RequestParam(required = false) Integer page,
                                        @RequestParam(required = false) Integer size) {
        PageRequest pageRequest = PageRequest.of(PageSupport.pageIndex(page), PageSupport.pageSize(size),
                Sort.by(Sort.Direction.ASC, "createdAt"));
        return PageSupport.of(userRepository.findAll(pageRequest), UserView::of);
    }

    @PostMapping("/users")
    public UserView createUser(@Valid @RequestBody CreateUserRequest request) {
        return userAdminService.create(request);
    }

    @PutMapping("/users/{id}")
    public UserView updateUser(@PathVariable String id, @Valid @RequestBody UpdateUserRequest request) {
        return userAdminService.update(id, request);
    }

    @PostMapping("/users/{id}/reset-password")
    public UserView resetPassword(@PathVariable String id, @Valid @RequestBody ResetPasswordRequest request) {
        return userAdminService.resetPassword(id, request);
    }

    @GetMapping("/model-configs")
    public ModelConfigListView modelConfigs() {
        return new ModelConfigListView(modelConfigService.list());
    }

    @PutMapping("/model-configs/{configType}")
    public ModelConfigView updateModelConfig(@PathVariable String configType,
                                             @RequestBody UpdateModelConfigRequest request) {
        return modelConfigService.update(configType, request);
    }

    @GetMapping("/audit-logs")
    public PageResponse<AuditLogView> auditLogs(@RequestParam(required = false) Integer page,
                                                @RequestParam(required = false) Integer size,
                                                @RequestParam(required = false) String action) {
        PageRequest pageRequest = PageRequest.of(PageSupport.pageIndex(page), PageSupport.pageSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        if (action == null || action.isBlank()) {
            return PageSupport.of(auditLogRepository.findAll(pageRequest), AuditLogView::of);
        }
        return PageSupport.of(auditLogRepository.findByAction(action.trim(), pageRequest), AuditLogView::of);
    }
}
