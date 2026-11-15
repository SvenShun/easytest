package com.easytest.contract;

import java.util.LinkedHashMap;
import java.util.Map;

/** Result of one engineering contract method. */
public record ContractResult(boolean passed, String summary, Map<String, Object> outputs) {

    public ContractResult {
        outputs = outputs == null ? Map.of() : Map.copyOf(outputs);
    }

    public static ContractResult ok(String summary) {
        return new ContractResult(true, summary, Map.of());
    }

    public static ContractResult ok(String summary, Map<String, Object> outputs) {
        return new ContractResult(true, summary, outputs);
    }

    public static ContractResult fail(String summary) {
        return new ContractResult(false, summary, Map.of());
    }

    public static ContractResult withOutput(String name, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(name, value);
        return new ContractResult(true, "ok", map);
    }
}
