package io.metersphere.uiexec;

import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.system.controller.handler.ResultHolder;
import io.metersphere.system.utils.SessionUtils;
import io.metersphere.uiexec.UiExecutionDtos.CreateTaskRequest;
import io.metersphere.uiexec.UiExecutionDtos.ResultCallback;
import io.metersphere.uiexec.UiExecutionDtos.StepCallback;
import io.metersphere.uiexec.UiExecutionDtos.TaskView;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * MeterSphere-native UI execution endpoints.
 *
 * <p>User endpoints use MeterSphere's Shiro session and permissions; device-agent
 * callbacks are anonymous at the Shiro layer but authenticated with the shared
 * {@code X-Agent-Token} header (constant-time comparison).</p>
 */
@RestController
@RequestMapping("/test-plan/ui-execution/tasks")
public class UiExecutionController {

    private final UiExecutionService service;
    private final UiExecutionProperties properties;

    public UiExecutionController(UiExecutionService service, UiExecutionProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @PostMapping
    @RequiresPermissions(PermissionConstants.TEST_PLAN_READ_EXECUTE)
    public ResultHolder create(@RequestBody CreateTaskRequest request) {
        TaskView task = service.create(request, SessionUtils.getUserId(), null);
        return ResultHolder.success(task);
    }

    @GetMapping("/{id}")
    @RequiresPermissions(PermissionConstants.TEST_PLAN_READ)
    public ResultHolder get(@PathVariable String id) {
        return ResultHolder.success(service.get(id));
    }

    @PostMapping("/{id}/steps")
    public ResultHolder step(@PathVariable String id,
                             @RequestHeader(value = "X-Agent-Token", required = false) String token,
                             @RequestBody StepCallback callback) {
        requireAgent(token);
        service.appendStep(id, callback);
        return ResultHolder.success("ok");
    }

    @PostMapping("/{id}/result")
    public ResultHolder result(@PathVariable String id,
                               @RequestHeader(value = "X-Agent-Token", required = false) String token,
                               @RequestBody ResultCallback callback) {
        requireAgent(token);
        service.complete(id, callback);
        return ResultHolder.success("ok");
    }

    @PostMapping("/{id}/logs")
    public ResultHolder logs(@PathVariable String id,
                             @RequestHeader(value = "X-Agent-Token", required = false) String token,
                             @RequestBody(required = false) Object ignored) {
        requireAgent(token);
        return ResultHolder.success("ok");
    }

    private void requireAgent(String token) {
        if (token == null || !MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8),
                properties.getAgentToken().getBytes(StandardCharsets.UTF_8))) {
            throw new org.apache.shiro.authz.UnauthorizedException("invalid agent token");
        }
    }
}
