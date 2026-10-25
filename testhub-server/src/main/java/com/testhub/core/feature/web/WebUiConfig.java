package com.testhub.core.feature.web;

import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;

/** Web console location helper; assets are streamed by {@link WebUiController}. */
@Configuration
public class WebUiConfig {

    public static Path staticRoot(WebUiProperties properties) {
        return Paths.get(properties.getStaticDir()).toAbsolutePath().normalize();
    }
}
