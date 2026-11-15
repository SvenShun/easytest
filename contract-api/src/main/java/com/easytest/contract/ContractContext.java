package com.easytest.contract;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Inputs, mutable outputs and execution log for one engineering contract method. */
public final class ContractContext {

    private final Map<String, Object> inputs;
    private final Map<String, Object> outputs = new LinkedHashMap<>();
    private final List<String> logs = new ArrayList<>();

    public ContractContext(Map<String, Object> inputs) {
        this.inputs = inputs == null ? Map.of() : new LinkedHashMap<>(inputs);
    }

    public Map<String, Object> inputs() {
        return inputs;
    }

    public Object input(String name) {
        return inputs.get(name);
    }

    public String requireString(String name) {
        Object value = inputs.get(name);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException("缺少契约输入变量: " + name);
        }
        return String.valueOf(value);
    }

    public void output(String name, Object value) {
        outputs.put(name, value);
    }

    public Map<String, Object> outputs() {
        return outputs;
    }

    public void log(String message) {
        if (message != null) logs.add(message);
    }

    public List<String> logs() {
        return logs;
    }
}
