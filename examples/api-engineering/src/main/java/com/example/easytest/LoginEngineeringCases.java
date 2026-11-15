package com.example.easytest;

import com.easytest.contract.ContractContext;
import com.easytest.contract.ContractResult;
import com.easytest.contract.EasyContract;

/**
 * Example engineering project. The platform stores only the contract YAML that
 * references {@code com.example.easytest.LoginEngineeringCases#loginWithCode}.
 */
public class LoginEngineeringCases {

    /**
     * Complex-case escape hatch: implement logic in the engineering project,
     * keep the behavior description on the method for platform display.
     */
    @EasyContract(
            id = "engineering.login",
            name = "代码实现的登录",
            description = "调用工程代码完成登录；平台只保存 methodPath、输入变量和行为说明。",
            inputs = {"username", "password"},
            outputs = {"token", "userId"},
            tags = {"engineering", "login"}
    )
    public ContractResult loginWithCode(ContractContext context) {
        String username = context.requireString("username");
        context.log("engineering login for " + username);
        // Replace this with real HTTP/SDK/DB code in an engineering project.
        context.output("token", "token-" + username);
        context.output("userId", "user-" + username);
        return ContractResult.ok("engineering login passed");
    }
}
