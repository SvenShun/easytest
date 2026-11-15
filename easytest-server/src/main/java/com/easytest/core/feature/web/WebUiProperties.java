package com.easytest.core.feature.web;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Static web-console serving ({@code easytest.web.static-dir}). */
@Component
@ConfigurationProperties(prefix = "easytest.web")
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
