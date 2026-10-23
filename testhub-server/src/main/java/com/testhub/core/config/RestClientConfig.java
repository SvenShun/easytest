package com.testhub.core.config;

import com.testhub.common.security.SecurityProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Outbound HTTP client used to talk to ui-execution (service token + JSON). */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient uiExecutionRestClient(CoreProperties coreProperties, SecurityProperties securityProperties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        return RestClient.builder()
                .baseUrl(trimTrailingSlash(coreProperties.getUiExecutionBaseUrl()))
                .requestFactory(factory)
                .defaultHeader("X-Service-Token", securityProperties.getServiceToken())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    private String trimTrailingSlash(String url) {
        if (url == null || url.isBlank()) {
            return "http://127.0.0.1:8081";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
