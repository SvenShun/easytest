package com.testhub.core.feature.notification;

import com.testhub.common.security.RequireRole;
import com.testhub.common.security.Role;
import com.testhub.core.feature.notification.NotificationDtos.ChannelRequest;
import com.testhub.core.feature.notification.NotificationDtos.ChannelView;
import com.testhub.core.feature.notification.NotificationDtos.TestResult;
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
@RequestMapping("/api/v1/notification-channels")
@RequireRole({Role.ADMIN, Role.TESTER, Role.BUSINESS, Role.OPS})
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public List<ChannelView> list() {
        return service.list();
    }

    @PostMapping
    @RequireRole({Role.ADMIN, Role.TESTER})
    public ChannelView create(@RequestBody ChannelRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @RequireRole({Role.ADMIN, Role.TESTER})
    public ChannelView update(@PathVariable String id, @RequestBody ChannelRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/test")
    @RequireRole({Role.ADMIN, Role.TESTER})
    public TestResult test(@PathVariable String id) {
        return service.test(id);
    }

    @DeleteMapping("/{id}")
    @RequireRole({Role.ADMIN, Role.TESTER})
    public void delete(@PathVariable String id) {
        service.delete(id);
    }
}
