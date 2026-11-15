package com.easytest.contract;

import java.util.List;

/** Machine-readable metadata generated from an {@link EasyContract} method. */
public record ContractDescriptor(String id, String name, String description, String methodPath,
                                 List<String> inputs, List<String> outputs, List<String> tags,
                                 ContractLayer layer, List<String> protocols) {

    public ContractDescriptor(String id, String name, String description, String methodPath,
                              List<String> inputs, List<String> outputs, List<String> tags) {
        this(id, name, description, methodPath, inputs, outputs, tags, ContractLayer.FLOW, List.of());
    }

    public ContractDescriptor {
        layer = layer == null ? ContractLayer.FLOW : layer;
        protocols = protocols == null ? List.of() : List.copyOf(protocols);
    }
}
