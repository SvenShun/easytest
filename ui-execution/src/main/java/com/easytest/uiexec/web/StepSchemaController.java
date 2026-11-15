package com.easytest.uiexec.web;

import com.easytest.common.security.RequireRole;
import com.easytest.common.security.Role;
import com.easytest.uiexec.domain.StepSpec;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Canonical UI STEP contract exposed to the platform editor. */
@RestController
@RequestMapping("/api/v1/ui-execution")
@RequireRole({Role.ADMIN, Role.TESTER})
public class StepSchemaController {

    @GetMapping("/step-schema")
    public Map<String, Object> schema() {
        return Map.of(
                "schemaVersion", 1,
                "stepTypes", StepSpec.SUPPORTED_TYPES,
                "selectorTypes", List.of("TEXT", "ID", "COORD"),
                "fields", List.of("seq", "stepType", "selectorType", "selector", "value",
                        "assertValue", "timeoutMs", "enabled"));
    }
}
