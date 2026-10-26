package com.testhub.uiexec.service;

import com.testhub.common.web.ApiException;
import com.testhub.uiexec.domain.DeviceGroupEntity;
import com.testhub.uiexec.repository.DeviceGroupRepository;
import com.testhub.uiexec.repository.DeviceRepository;
import com.testhub.uiexec.web.dto.DeviceGroupDtos.GroupRequest;
import com.testhub.uiexec.web.dto.DeviceGroupDtos.GroupView;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeviceGroupServiceTest {

    private final DeviceGroupRepository groups = mock(DeviceGroupRepository.class);
    private final DeviceRepository devices = mock(DeviceRepository.class);
    private final DeviceGroupService service = new DeviceGroupService(groups, devices);

    @Test
    void createsGroupWithExistingDevices() {
        when(devices.existsById("device-1")).thenReturn(true);
        when(groups.findByName("真机组")).thenReturn(Optional.empty());
        when(groups.save(any(DeviceGroupEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        GroupView view = service.create(new GroupRequest(" 真机组 ", "lab", List.of("device-1", "device-1")));

        assertThat(view.name()).isEqualTo("真机组");
        assertThat(view.deviceIds()).containsExactly("device-1");
        assertThat(view.deviceCount()).isEqualTo(1);
    }

    @Test
    void rejectsUnknownDeviceAndDuplicateName() {
        when(groups.findByName("真机组")).thenReturn(Optional.of(
                new DeviceGroupEntity("g1", "真机组", null, Set.of(), Instant.now(), Instant.now())));
        assertThatThrownBy(() -> service.create(new GroupRequest("真机组", null, List.of())))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("已存在");

        when(groups.findById("g1")).thenReturn(Optional.of(
                new DeviceGroupEntity("g1", "真机组", null, Set.of(), Instant.now(), Instant.now())));
        when(devices.existsById("missing")).thenReturn(false);
        assertThatThrownBy(() -> service.update("g1", new GroupRequest("新组", null, List.of("missing"))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("设备不存在");
    }

    @Test
    void updateReplacesMembership() {
        DeviceGroupEntity group = new DeviceGroupEntity("g1", "真机组", "old",
                Set.of("device-1"), Instant.now(), Instant.now());
        when(groups.findById("g1")).thenReturn(Optional.of(group));
        when(groups.findByName("真机组")).thenReturn(Optional.of(group));
        when(devices.existsById("device-2")).thenReturn(true);
        when(groups.save(any(DeviceGroupEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        GroupView view = service.update("g1", new GroupRequest("真机组", "new", List.of("device-2")));

        assertThat(view.deviceIds()).containsExactly("device-2");
        assertThat(view.description()).isEqualTo("new");
    }
}
