package com.smarthelp.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Production uses an OIDC JWT resource server. Local development remains open
 * until an issuer/client is configured; it must never be used for deployment.
 */
@Configuration
public class SecurityConfig {

    @Bean
    @ConditionalOnProperty(name = "smarthelp.security.oidc-enabled", havingValue = "true")
    JwtDecoder jwtDecoder(@Value("${SMARTHELP_OIDC_ISSUER_URI}") String issuerUri) {
        if (issuerUri.isBlank()) {
            throw new IllegalStateException("SMARTHELP_OIDC_ISSUER_URI is required when OIDC is enabled");
        }
        return JwtDecoders.fromIssuerLocation(issuerUri);
    }

    @Bean
    @ConditionalOnProperty(name = "smarthelp.security.oidc-enabled", havingValue = "true")
    SecurityFilterChain oidcSecurity(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()))
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "smarthelp.security.oidc-enabled", havingValue = "false", matchIfMissing = true)
    SecurityFilterChain localDevelopmentSecurity(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .build();
    }
}
