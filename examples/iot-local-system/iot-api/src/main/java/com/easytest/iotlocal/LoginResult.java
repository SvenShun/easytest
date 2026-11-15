package com.easytest.iotlocal;

import java.io.Serializable;

public class LoginResult implements Serializable {
    private boolean ok;
    private String token;
    private String userId;

    public LoginResult() {
    }

    public LoginResult(boolean ok, String token, String userId) {
        this.ok = ok;
        this.token = token;
        this.userId = userId;
    }

    public boolean isOk() { return ok; }
    public void setOk(boolean ok) { this.ok = ok; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
}
