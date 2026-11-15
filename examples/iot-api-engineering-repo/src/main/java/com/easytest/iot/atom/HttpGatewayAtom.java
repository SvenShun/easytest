package com.easytest.iot.atom;

import com.easytest.contract.ContractContext;
import com.easytest.contract.ContractLayer;
import com.easytest.contract.ContractResult;
import com.easytest.contract.EasyContract;
import com.easytest.engineering.HttpAtom;
import com.easytest.iot.config.IotConfig;

/** ATOM: fixed gateway login call; action/flow layers reuse this operation. */
public class HttpGatewayAtom {

    @EasyContract(id = "iot.atom.http.login", name = "网关登录", layer = ContractLayer.ATOM,
            protocols = {"HTTP"}, description = "从 gateway 发起登录，返回 token",
            inputs = {"username", "password"}, outputs = {"token", "status"}, tags = {"iot", "http"})
    public ContractResult login(ContractContext context) {
        String username = context.requireString("username");
        String password = context.requireString("password");
        String body = "{\"username\":\"" + escape(username) + "\",\"password\":\"" + escape(password) + "\"}";
        HttpAtom.Response response = HttpAtom.postJson(IotConfig.gateway() + "/api/auth/login",
                body, IotConfig.jsonHeaders(), 10_000);
        context.log("gateway login status=" + response.status());
        if (!response.ok()) return ContractResult.fail("网关登录失败 status=" + response.status());
        context.output("status", response.status());
        context.output("token", extractToken(response.body()));
        return ContractResult.ok("网关登录成功");
    }

    private String extractToken(String body) {
        // Demo keeps a tiny JSON extraction; production uses Jackson.
        int index = body.indexOf("\"token\"");
        if (index < 0) return body;
        int start = body.indexOf('"', body.indexOf(':', index) + 1) + 1;
        int end = body.indexOf('"', start);
        return start > 0 && end > start ? body.substring(start, end) : body;
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
