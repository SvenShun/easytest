package com.example.gitdemo;

import com.easytest.contract.ContractContext;
import com.easytest.contract.ContractResult;
import com.easytest.contract.EasyContract;

/** Demo API engineering cases scanned from easytest.api.yaml. */
public class GitDemoCases {

    @EasyContract(id = "git.login", name = "Git 仓库登录",
            description = "来自独立 API 自动化工程仓库的登录测试方法",
            inputs = {"username", "password"}, outputs = {"token", "userId"})
    public ContractResult login(ContractContext context) {
        String username = context.requireString("username");
        context.log("git demo login for " + username);
        context.output("token", "git-token-" + username);
        context.output("userId", "git-user-" + username);
        return ContractResult.ok("git login passed");
    }

    @EasyContract(id = "git.health", name = "Git 仓库健康检查",
            description = "来自独立 API 自动化工程仓库的健康检查方法",
            outputs = {"status"})
    public ContractResult health(ContractContext context) {
        context.output("status", "UP");
        return ContractResult.ok("git health ok");
    }
}
