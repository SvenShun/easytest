package com.easytest.contract;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method in a test engineering project as an executable contract case.
 * The method must accept {@link ContractContext} and return {@link ContractResult}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface EasyContract {

    /** Stable contract id used by platform records. */
    String id();

    /** Human readable case name. */
    String name() default "";

    /** Behaviour description shown in the platform (equivalent to code comments). */
    String description() default "";

    /** Variable names supplied by the platform. */
    String[] inputs() default {};

    /** Variable names produced back to the platform. */
    String[] outputs() default {};

    String[] tags() default {};

    /** Engineering layer: FLOW = business case, ACTION = reusable combination, ATOM = fixed operation. */
    ContractLayer layer() default ContractLayer.FLOW;

    /** Protocols used by this method, e.g. HTTP, RPC, KAFKA, MQTT, SQL, REDIS. */
    String[] protocols() default {};

    /** Optional JSON schema/description for inputs, shown by the platform. */
    String inputSchema() default "";

    /** Optional JSON schema/description for outputs, shown by the platform. */
    String outputSchema() default "";
}
