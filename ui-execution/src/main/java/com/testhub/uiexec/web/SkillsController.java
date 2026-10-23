package com.testhub.uiexec.web;

import com.testhub.common.security.CurrentUser;
import com.testhub.common.security.RequireRole;
import com.testhub.common.security.Role;
import com.testhub.uiexec.domain.SkillsPointerEntity;
import com.testhub.uiexec.service.SkillsService;
import com.testhub.uiexec.web.dto.SkillsDtos.SkillsPointerUpdateRequest;
import com.testhub.uiexec.web.dto.SkillsDtos.SkillsPointerView;
import com.testhub.uiexec.web.dto.SkillsDtos.SkillsRollbackRequest;
import com.testhub.uiexec.web.dto.SkillsDtos.SkillsSyncResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Skills pointer APIs (docs/API-CONTRACT.md §2.5). */
@RestController
@RequestMapping("/api/v1/ui-execution/skills")
public class SkillsController {

    private final SkillsService skillsService;

    public SkillsController(SkillsService skillsService) {
        this.skillsService = skillsService;
    }

    @GetMapping("/pointer")
    public SkillsPointerView pointer() {
        return skillsService.view();
    }

    @PutMapping("/pointer")
    @RequireRole(Role.ADMIN)
    public SkillsPointerView update(@RequestBody SkillsPointerUpdateRequest request) {
        SkillsPointerEntity pointer = skillsService.update(request.gitUrl(), request.branch(), actor());
        return toView(pointer);
    }

    @PostMapping("/pointer/sync")
    @RequireRole(Role.ADMIN)
    public SkillsSyncResponse sync() {
        return skillsService.sync(actor());
    }

    @PostMapping("/pointer/rollback")
    @RequireRole(Role.ADMIN)
    public SkillsPointerView rollback(@RequestBody SkillsRollbackRequest request) {
        SkillsPointerEntity pointer = skillsService.rollback(request.commit(), actor());
        return toView(pointer);
    }

    private SkillsPointerView toView(SkillsPointerEntity pointer) {
        return new SkillsPointerView(pointer.getGitUrl(), pointer.getBranch(), pointer.getActiveCommit(),
                pointer.getUpdatedBy(), pointer.getUpdatedAt(), pointer.getSyncedAt(),
                skillsService.configured(pointer));
    }

    private String actor() {
        var principal = CurrentUser.get();
        return principal == null ? "system" : principal.username();
    }
}
