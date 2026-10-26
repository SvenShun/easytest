package com.testhub.uiexec.service;

import com.testhub.common.util.Ids;
import com.testhub.common.web.ApiException;
import com.testhub.uiexec.domain.DeviceEntity;
import com.testhub.uiexec.domain.DeviceGroupEntity;
import com.testhub.uiexec.repository.DeviceGroupRepository;
import com.testhub.uiexec.repository.DeviceRepository;
import com.testhub.uiexec.web.dto.DeviceGroupDtos.GroupRequest;
import com.testhub.uiexec.web.dto.DeviceGroupDtos.GroupView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Service
public class DeviceGroupService {

    private final DeviceGroupRepository groups;
    private final DeviceRepository devices;

    public DeviceGroupService(DeviceGroupRepository groups, DeviceRepository devices) {
        this.groups = groups;
        this.devices = devices;
    }

    public List<GroupView> list() {
        return groups.findAllByOrderByNameAsc().stream().map(this::view).toList();
    }

    public GroupView get(String id) {
        return view(require(id));
    }

    @Transactional
    public GroupView create(GroupRequest request) {
        validate(request);
        if (groups.findByName(request.name().trim()).isPresent()) {
            throw ApiException.conflict("分组名称已存在: " + request.name());
        }
        Instant now = Instant.now();
        return view(groups.save(new DeviceGroupEntity(Ids.newId("dgroup"), request.name().trim(),
                blankToNull(request.description()), validDeviceIds(request.deviceIds()), now, now)));
    }

    @Transactional
    public GroupView update(String id, GroupRequest request) {
        validate(request);
        DeviceGroupEntity group = require(id);
        groups.findByName(request.name().trim()).ifPresent(other -> {
            if (!other.getId().equals(id)) {
                throw ApiException.conflict("分组名称已存在: " + request.name());
            }
        });
        group.setName(request.name().trim());
        group.setDescription(blankToNull(request.description()));
        group.setDeviceIds(validDeviceIds(request.deviceIds()));
        group.setUpdatedAt(Instant.now());
        return view(groups.save(group));
    }

    @Transactional
    public void delete(String id) {
        groups.delete(require(id));
    }

    private void validate(GroupRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw ApiException.badRequest("分组名称不能为空");
        }
        if (request.name().trim().length() > 128) {
            throw ApiException.badRequest("分组名称不能超过 128 个字符");
        }
    }

    private LinkedHashSet<String> validDeviceIds(List<String> deviceIds) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (deviceIds == null) {
            return result;
        }
        for (String deviceId : deviceIds) {
            if (deviceId == null || deviceId.isBlank()) {
                continue;
            }
            String id = deviceId.trim();
            if (!devices.existsById(id)) {
                throw ApiException.badRequest("设备不存在: " + id);
            }
            result.add(id);
        }
        return result;
    }

    private DeviceGroupEntity require(String id) {
        return groups.findById(id).orElseThrow(() -> ApiException.notFound("分组不存在: " + id));
    }

    private GroupView view(DeviceGroupEntity group) {
        List<String> ids = List.copyOf(group.getDeviceIds());
        return new GroupView(group.getId(), group.getName(), group.getDescription(), ids,
                ids.size(), group.getCreatedAt(), group.getUpdatedAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
