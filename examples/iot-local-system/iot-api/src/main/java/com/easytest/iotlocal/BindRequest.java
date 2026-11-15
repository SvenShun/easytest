package com.easytest.iotlocal;

import java.io.Serializable;

public class BindRequest implements Serializable {
    private String homeId;
    private String deviceId;

    public BindRequest() {
    }

    public BindRequest(String homeId, String deviceId) {
        this.homeId = homeId;
        this.deviceId = deviceId;
    }

    public String getHomeId() { return homeId; }
    public void setHomeId(String homeId) { this.homeId = homeId; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
}
