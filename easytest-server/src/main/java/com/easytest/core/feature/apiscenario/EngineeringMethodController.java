package com.easytest.core.feature.apiscenario;

import com.easytest.common.security.RequireRole;
import com.easytest.common.security.Role;
import com.easytest.core.feature.plan.ApiExecutionClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Contract methods loaded by API agents, shown to platform editors. */
@RestController
@RequestMapping("/api/v1/engineering-methods")
@RequireRole({Role.ADMIN, Role.TESTER})
public class EngineeringMethodController {

    private final ApiExecutionClient apiExecutionClient;

    public EngineeringMethodController(ApiExecutionClient apiExecutionClient) {
        this.apiExecutionClient = apiExecutionClient;
    }

    @GetMapping
    public List<ApiExecutionClient.EngineeringMethod> list() {
        return apiExecutionClient.listEngineeringMethods();
    }
}
