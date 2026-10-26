package com.testhub.uiexec.web;

import com.testhub.common.security.RequireRole;
import com.testhub.common.security.Role;
import com.testhub.uiexec.service.DeviceGroupService;
import com.testhub.uiexec.web.dto.DeviceGroupDtos.GroupRequest;
import com.testhub.uiexec.web.dto.DeviceGroupDtos.GroupView;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ui-execution/device-groups")
@RequireRole({Role.ADMIN, Role.TESTER, Role.BUSINESS, Role.OPS})
public class DeviceGroupController {

    private final DeviceGroupService service;

    public DeviceGroupController(DeviceGroupService service) {
        this.service = service;
    }

    @GetMapping
    public List<GroupView> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public GroupView get(@PathVariable String id) {
        return service.get(id);
    }

    @PostMapping
    @RequireRole({Role.ADMIN, Role.TESTER})
    public GroupView create(@RequestBody GroupRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @RequireRole({Role.ADMIN, Role.TESTER})
    public GroupView update(@PathVariable String id, @RequestBody GroupRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @RequireRole({Role.ADMIN, Role.TESTER})
    public void delete(@PathVariable String id) {
        service.delete(id);
    }
}
