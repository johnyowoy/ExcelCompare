package com.johnyowoy.compare.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class AppConfig {

    @Bean
    public Map<String, Map<String, Object>> uploadedFiles() {
        return new HashMap<>();
    }
}