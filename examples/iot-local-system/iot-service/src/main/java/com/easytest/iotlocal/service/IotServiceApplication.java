package com.easytest.iotlocal.service;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableDubbo
public class IotServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(IotServiceApplication.class, args);
    }
}
