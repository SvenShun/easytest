package com.easytest.iotlocal;

import java.io.Serializable;

public class BindResult implements Serializable {
    private boolean ok;
    private String homeId;
    private String deviceId;
    private String message;

    public BindResult() {
    }

    public BindResult(boolean ok, String homeId, String deviceId, String message) {
        this.ok = ok;
        this.homeId = homeId;
        this.deviceId = deviceId;
        this.message = message;
    }

    public boolean isOk() { return ok; }
    public void setOk(boolean ok) { this.ok = ok; }
    public String getHomeId() { return homeId; }
    public void setHomeId(String homeId) { this.homeId = homeId; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
