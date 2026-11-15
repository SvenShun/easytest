package com.easytest.iotlocal;

import java.io.Serializable;

public class DeviceInfo implements Serializable {
    private String deviceId;
    private String homeId;
    private String status;

    public DeviceInfo() {
    }

    public DeviceInfo(String deviceId, String homeId, String status) {
        this.deviceId = deviceId;
        this.homeId = homeId;
        this.status = status;
    }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getHomeId() { return homeId; }
    public void setHomeId(String homeId) { this.homeId = homeId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
