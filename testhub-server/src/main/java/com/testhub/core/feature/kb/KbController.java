package com.testhub.core.feature.kb;

import com.testhub.common.security.RequireRole;
import com.testhub.common.security.Role;
import com.testhub.core.feature.kb.KbDtos.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/kb")
@RequireRole({Role.ADMIN, Role.TESTER, Role.BUSINESS, Role.OPS})
public class KbController {

    private final KbService service;

    public KbController(KbService service) {
        this.service = service;
    }

    @GetMapping("/apps")
    public List<AppView> apps() {
        return service.listApps();
    }

    @GetMapping("/apps/{id}")
    public AppView app(@PathVariable String id) {
        return service.getApp(id);
    }

    @PostMapping("/apps")
    @RequireRole({Role.ADMIN, Role.TESTER})
    public AppView createApp(@RequestBody AppRequest request) {
        return service.createApp(request);
    }

    @PutMapping("/apps/{id}")
    @RequireRole({Role.ADMIN, Role.TESTER})
    public AppView updateApp(@PathVariable String id, @RequestBody AppRequest request) {
        return service.updateApp(id, request);
    }

    @DeleteMapping("/apps/{id}")
    @RequireRole({Role.ADMIN, Role.TESTER})
    public void deleteApp(@PathVariable String id) {
        service.deleteApp(id);
    }

    @GetMapping("/apps/{appId}/pages")
    public List<PageView> pages(@PathVariable String appId) {
        return service.listPages(appId);
    }

    @GetMapping("/pages/{id}")
    public PageView page(@PathVariable String id) {
        return service.getPage(id);
    }

    @PostMapping("/apps/{appId}/pages")
    @RequireRole({Role.ADMIN, Role.TESTER})
    public PageView createPage(@PathVariable String appId, @RequestBody PageRequest request) {
        return service.createPage(appId, request);
    }

    @PutMapping("/pages/{id}")
    @RequireRole({Role.ADMIN, Role.TESTER})
    public PageView updatePage(@PathVariable String id, @RequestBody PageRequest request) {
        return service.updatePage(id, request);
    }

    @DeleteMapping("/pages/{id}")
    @RequireRole({Role.ADMIN, Role.TESTER})
    public void deletePage(@PathVariable String id) {
        service.deletePage(id);
    }

    @GetMapping("/pages/{pageId}/controls")
    public List<ControlView> controls(@PathVariable String pageId) {
        return service.listControls(pageId);
    }

    @GetMapping("/controls/{id}")
    public ControlView control(@PathVariable String id) {
        return service.getControl(id);
    }

    @PostMapping("/pages/{pageId}/controls")
    @RequireRole({Role.ADMIN, Role.TESTER})
    public ControlView createControl(@PathVariable String pageId, @RequestBody ControlRequest request) {
        return service.createControl(pageId, request);
    }

    @PostMapping("/pages/{pageId}/controls/batch")
    @RequireRole({Role.ADMIN, Role.TESTER})
    public List<ControlView> batchControls(@PathVariable String pageId, @RequestBody BatchControlRequest request) {
        return service.batchControls(pageId, request);
    }

    @PutMapping("/controls/{id}")
    @RequireRole({Role.ADMIN, Role.TESTER})
    public ControlView updateControl(@PathVariable String id, @RequestBody ControlRequest request) {
        return service.updateControl(id, request);
    }

    @DeleteMapping("/controls/{id}")
    @RequireRole({Role.ADMIN, Role.TESTER})
    public void deleteControl(@PathVariable String id) {
        service.deleteControl(id);
    }

    @GetMapping("/controls/{controlId}/actions")
    public List<ActionView> actions(@PathVariable String controlId) {
        return service.listActions(controlId);
    }

    @PostMapping("/controls/{controlId}/actions")
    @RequireRole({Role.ADMIN, Role.TESTER})
    public ActionView createAction(@PathVariable String controlId, @RequestBody ActionRequest request) {
        return service.createAction(controlId, request);
    }

    @PutMapping("/actions/{id}")
    @RequireRole({Role.ADMIN, Role.TESTER})
    public ActionView updateAction(@PathVariable String id, @RequestBody ActionRequest request) {
        return service.updateAction(id, request);
    }

    @DeleteMapping("/actions/{id}")
    @RequireRole({Role.ADMIN, Role.TESTER})
    public void deleteAction(@PathVariable String id) {
        service.deleteAction(id);
    }
}
