package com.testhub.core.feature.script;

import com.testhub.common.security.RequireRole;
import com.testhub.common.security.Role;
import com.testhub.common.web.PageResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/scripts")
@RequireRole({Role.ADMIN, Role.TESTER, Role.BUSINESS, Role.OPS})
public class ScriptController {

    private final ScriptService scriptService;

    public ScriptController(ScriptService scriptService) {
        this.scriptService = scriptService;
    }

    @RequireRole({Role.ADMIN, Role.TESTER})
    @PostMapping(consumes = "multipart/form-data")
    public ScriptView upload(@RequestParam("file") MultipartFile file,
                             @RequestParam(required = false) String name,
                             @RequestParam String scriptType,
                             @RequestParam(required = false) String entryCommand) {
        return scriptService.upload(file, name, scriptType, entryCommand);
    }

    @GetMapping
    public PageResponse<ScriptView> list(@RequestParam(required = false) Integer page,
                                         @RequestParam(required = false) Integer size) {
        return scriptService.list(page, size);
    }

    @GetMapping("/{id}")
    public ScriptView get(@PathVariable String id) {
        return scriptService.get(id);
    }

    @RequireRole({Role.ADMIN, Role.TESTER})
    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        scriptService.delete(id);
    }
}
