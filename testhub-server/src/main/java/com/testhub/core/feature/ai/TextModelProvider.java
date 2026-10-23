package com.testhub.core.feature.ai;

import java.util.List;

/** Text-to-BDD model provider port. */
public interface TextModelProvider {

    boolean supports(String provider);

    List<BddDraft> generate(TextModelInput input);
}
