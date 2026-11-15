package com.easytest.iotlocal.service;

import com.easytest.iotlocal.LoginRequest;
import com.easytest.iotlocal.LoginResult;
import com.easytest.iotlocal.UserFacade;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.jdbc.core.JdbcTemplate;

@DubboService
public class UserFacadeImpl implements UserFacade {
    private final JdbcTemplate jdbc;
    public UserFacadeImpl(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public LoginResult login(LoginRequest request) {
        if (request == null || request.getUsername() == null) return new LoginResult(false, null, null);
        var rows = jdbc.queryForList("SELECT user_id FROM app_user WHERE username = ? AND password = ?",
                request.getUsername(), request.getPassword());
        if (rows.isEmpty()) return new LoginResult(false, null, null);
        String userId = String.valueOf(rows.get(0).get("user_id"));
        return new LoginResult(true, "token-" + userId, userId);
    }
}
