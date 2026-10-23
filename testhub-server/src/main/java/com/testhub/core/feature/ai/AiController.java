package com.testhub.core.feature.ai;

import com.testhub.common.security.RequireRole;
import com.testhub.common.security.Role;
import com.testhub.common.web.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
@RequireRole({Role.ADMIN, Role.TESTER, Role.BUSINESS})
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/bdd-drafts")
    public GenerateBddResponse generate(@RequestBody GenerateBddRequest request) {
        return aiService.generate(request);
    }

    @GetMapping("/jobs")
    public PageResponse<AiJobView> jobs(@RequestParam(required = false) Integer page,
                                        @RequestParam(required = false) Integer size) {
        return aiService.listJobs(page, size);
    }

    @GetMapping("/jobs/{id}")
    public AiJobView job(@PathVariable String id) {
        return aiService.getJob(id);
    }
}
