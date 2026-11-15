package com.easytest.contract;

import java.util.List;

/** Machine-readable metadata generated from an {@link EasyContract} method. */
public record ContractDescriptor(String id, String name, String description, String methodPath,
                                 List<String> inputs, List<String> outputs, List<String> tags) {
}
