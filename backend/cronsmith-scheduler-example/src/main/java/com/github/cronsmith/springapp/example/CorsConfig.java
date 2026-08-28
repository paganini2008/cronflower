package com.github.cronsmith.springapp.example;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Opens CORS for the cronflower frontend, which is served from its own origin (the Angular dev server
 * on :4200, or wherever it is deployed). Origins are configurable via {@code cronsmith.demo.cors-origins}
 * (comma-separated patterns; default {@code *}). Uses {@code allowedOriginPatterns} so {@code *} works
 * without credentials.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${cronsmith.demo.cors-origins:*}")
    private String origins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**").allowedOriginPatterns(origins.split(","))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS").allowedHeaders("*");
    }

}
