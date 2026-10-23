package com.testhub.core.feature.internal;

import com.testhub.core.feature.system.ModelConfigService;
import com.testhub.core.feature.system.ModelCredential;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Service-token only endpoint that distributes model credentials. */
@RestController
@RequestMapping("/internal/v1/model-configs")
public class InternalModelConfigController {

    private final ModelConfigService modelConfigService;

    public InternalModelConfigController(ModelConfigService modelConfigService) {
        this.modelConfigService = modelConfigService;
    }

    @GetMapping("/{configType}/credential")
    public ModelCredential credential(@PathVariable String configType) {
        return modelConfigService.requireCredential(configType);
    }
}
