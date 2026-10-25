package com.testhub.core.feature.web;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Static web-console serving ({@code testhub.web.static-dir}). */
@Component
@ConfigurationProperties(prefix = "testhub.web")
public class WebUiProperties {
    /** Directory containing the built Vite console (index.html + assets). */
    private String staticDir = "web/dist";

    public String getStaticDir() {
        return staticDir;
    }

    public void setStaticDir(String staticDir) {
        this.staticDir = staticDir;
    }
}
