package com.easytest.apiexec.contract;

import com.easytest.contract.ContractDescriptor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Contract methods currently loaded by this API agent. */
@RestController
@RequestMapping("/api/v1/api-execution/contracts")
public class ContractController {

    private final EngineeringMethodRegistry registry;

    public ContractController(EngineeringMethodRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public List<ContractDescriptor> list() {
        return registry.list();
    }

    @PostMapping("/reload")
    public List<ContractDescriptor> reload() {
        registry.reload();
        return registry.list();
    }
}
