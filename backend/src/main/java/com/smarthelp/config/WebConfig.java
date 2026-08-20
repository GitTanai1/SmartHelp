package com.smarthelp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.stream.Stream;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Primary frontend origin — set via SMARTHELP_FRONTEND_ORIGIN.
     * In production this is the exact Vercel deployment URL, e.g.
     * https://smarthelp.vercel.app
     */
    private final String frontendOrigin;

    /**
     * Optional comma-separated list of extra allowed origins.
     * Use SMARTHELP_EXTRA_ORIGINS to whitelist Vercel preview deployments, e.g.
     * https://smarthelp-git-main-yourteam.vercel.app,https://smarthelp-*.vercel.app
     *
     * Note: Spring MVC does not support wildcard subdomains in allowedOrigins.
     * For preview URLs add each one explicitly, or use allowedOriginPatterns instead.
     */
    private final String extraOrigins;

    public WebConfig(
            @Value("${smarthelp.frontend.origin:http://localhost:4200}") String frontendOrigin,
            @Value("${smarthelp.extra.origins:}") String extraOrigins) {
        this.frontendOrigin = frontendOrigin;
        this.extraOrigins = extraOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Always include the primary origin + local dev origins
        Stream<String> base = Stream.of(
                frontendOrigin,
                "http://localhost:4200",
                "http://127.0.0.1:4200"
        );

        // Append any extra comma-separated origins from the env var
        Stream<String> extra = extraOrigins.isBlank()
                ? Stream.empty()
                : Arrays.stream(extraOrigins.split(",")).map(String::trim).filter(s -> !s.isBlank());

        String[] origins = Stream.concat(base, extra).distinct().toArray(String[]::new);

        registry.addMapping("/api/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
