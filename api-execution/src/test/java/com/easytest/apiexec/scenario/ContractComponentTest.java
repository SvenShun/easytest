package com.easytest.apiexec.scenario;

import com.easytest.apiexec.assertion.AssertionEvaluator;
import com.easytest.apiexec.config.ApiExecProperties;
import com.easytest.apiexec.contract.EngineeringMethodRegistry;
import com.easytest.apiexec.execution.HttpExecutor;
import com.easytest.contract.ContractContext;
import com.easytest.contract.ContractResult;
import com.easytest.contract.EasyContract;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ContractComponentTest {

    public static class DemoEngineeringCases {
        @EasyContract(id = "demo.login", name = "工程登录", description = "调用工程代码完成登录",
                inputs = {"username"}, outputs = {"token"})
        public ContractResult login(ContractContext context) {
            String username = context.requireString("username");
            context.log("login " + username);
            return ContractResult.ok("工程登录通过", Map.of("token", "token-" + username));
        }
    }

    @Test
    void executesAnnotatedEngineeringMethodFromYamlContract() {
        ApiExecProperties properties = new ApiExecProperties();
        EngineeringMethodRegistry registry = new EngineeringMethodRegistry(properties);
        registry.registerClass(DemoEngineeringCases.class);
        ScenarioExecutor executor = new ScenarioExecutor(new ScenarioYamlParser(), new HttpExecutor(),
                new AssertionEvaluator(new ObjectMapper()), new ObjectMapper(), registry);
        String yaml = """
                name: engineering-contract
                variables:
                  username: tester
                steps:
                  - name: engineering login
                    type: component
                    methodPath: %s#login
                    inputs:
                      username: "${username}"
                    outputs: [token]
                  - name: verify token
                    type: assert
                    assertions:
                      - {type: VARIABLE, path: token, operator: EQ, expected: token-tester}
                """.formatted(DemoEngineeringCases.class.getName());
        ScenarioRunResult result = executor.execute(yaml, Map.of(), Map.of(), 30, () -> false);
        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.variables()).containsEntry("token", "token-tester");
        assertThat(result.steps()).anySatisfy(step -> {
            assertThat(step.type()).isEqualTo("component");
            assertThat(step.status()).isEqualTo("OK");
            assertThat(step.response()).containsEntry("passed", true);
        });
    }

    @Test
    void failsWhenDeclaredOutputIsMissing() {
        ScenarioExecutor executor = new ScenarioExecutor(new ScenarioYamlParser(), new HttpExecutor(),
                new AssertionEvaluator(new ObjectMapper()), new ObjectMapper());
        EngineeringMethodRegistry registry = new EngineeringMethodRegistry(new ApiExecProperties());
        registry.registerClass(DemoEngineeringCases.class);
        executor = new ScenarioExecutor(new ScenarioYamlParser(), new HttpExecutor(),
                new AssertionEvaluator(new ObjectMapper()), new ObjectMapper(), registry);
        String yaml = """
                name: missing-output
                steps:
                  - name: login
                    type: component
                    methodPath: %s#login
                    inputs: {username: tester}
                    outputs: [notThere]
                """.formatted(DemoEngineeringCases.class.getName());
        ScenarioRunResult result = executor.execute(yaml, Map.of(), Map.of(), 30, () -> false);
        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.steps()).anySatisfy(step -> assertThat(step.error()).contains("CONTRACT_OUTPUT_MISSING"));
    }

    @Test
    void failsWhenEngineeringMethodIsMissing() {
        ScenarioExecutor executor = new ScenarioExecutor(new ScenarioYamlParser(), new HttpExecutor(),
                new AssertionEvaluator(new ObjectMapper()), new ObjectMapper(),
                new EngineeringMethodRegistry(new ApiExecProperties()));
        String yaml = """
                name: missing-method
                steps:
                  - {name: missing, type: component, methodPath: no.such#method}
                """;
        ScenarioRunResult result = executor.execute(yaml, Map.of(), Map.of(), 30, () -> false);
        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.steps()).anySatisfy(step -> assertThat(step.error()).contains("METHOD_NOT_FOUND"));
    }
}
