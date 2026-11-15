package com.easytest.core.feature.apiscenario.git;

import com.easytest.common.security.PublicApi;
import com.easytest.common.security.RequireRole;
import com.easytest.common.security.Role;
import com.easytest.core.feature.apiscenario.git.ScenarioGitDtos.ConfigRequest;
import com.easytest.core.feature.apiscenario.git.ScenarioGitDtos.ConfigView;
import com.easytest.core.feature.apiscenario.git.ScenarioGitDtos.SyncResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.easytest.common.web.ApiException;

@RestController
@RequestMapping("/api/v1/api-scenarios/git")
@RequireRole({Role.ADMIN, Role.TESTER})
public class ScenarioGitController {

    private final ScenarioGitSyncService service;

    public ScenarioGitController(ScenarioGitSyncService service) {
        this.service = service;
    }

    @GetMapping("/config")
    public ConfigView config() {
        return service.config();
    }

    @PutMapping("/config")
    public ConfigView update(@RequestBody ConfigRequest request) {
        return service.update(request);
    }

    @PostMapping("/sync")
    public SyncResult sync() {
        return service.sync("manual-sync");
    }

    /**
     * GitHub/GitLab webhook. Authenticate with X-Webhook-Token or GitHub's
     * X-Hub-Signature-256 HMAC; never expose this endpoint without a secret.
     */
    @PublicApi
    @PostMapping("/webhook")
    public SyncResult webhook(@RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
                              @RequestHeader(value = "X-Webhook-Token", required = false) String token,
                              @RequestBody(required = false) String body) {
        if (!service.verifyWebhook(signature, token, body == null ? "" : body)) {
            throw ApiException.unauthorized("invalid webhook secret");
        }
        return service.sync("git-webhook");
    }
}
