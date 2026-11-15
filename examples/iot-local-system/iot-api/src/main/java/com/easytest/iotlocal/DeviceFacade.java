package com.easytest.iotlocal;

public interface DeviceFacade {
    BindResult bind(BindRequest request);
    DeviceInfo getDevice(String deviceId);
}
