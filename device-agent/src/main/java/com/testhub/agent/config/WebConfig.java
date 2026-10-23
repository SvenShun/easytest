package com.testhub.agent.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.testhub.agent.http.AgentTokenInterceptor;

/** Registers the agent token interceptor for agent and device endpoints. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AgentTokenInterceptor agentTokenInterceptor;

    public WebConfig(AgentTokenInterceptor agentTokenInterceptor) {
        this.agentTokenInterceptor = agentTokenInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(agentTokenInterceptor)
                .addPathPatterns("/agent/**", "/device/**");
    }
}
