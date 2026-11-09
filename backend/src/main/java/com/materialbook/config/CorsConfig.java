package com.materialbook.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/** Only needed when a browser app on ANOTHER origin calls the API (e.g. `vite dev` on :5173); blank = no CORS. Picked up automatically by Spring Security's {@code .cors(withDefaults())}. */
@Configuration
public class CorsConfig {
    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origin}") String origin) {
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        if (origin == null || origin.isBlank()) {
            return src; // same-origin deployment (the app serves the SPA itself): no cross-origin caller is allowed
        }
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(List.of(origin));
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        c.setAllowCredentials(true);
        c.setMaxAge(3600L);
        src.registerCorsConfiguration("/**", c);
        return src;
    }
}
