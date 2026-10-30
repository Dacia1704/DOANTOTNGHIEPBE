package com.tamdaptit.rentalmanagement.common.config;

import com.tamdaptit.rentalmanagement.common.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;

@Configuration
@org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication(
        type = org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET)
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    private static final String[] PUBLIC_ENDPOINTS = {
            // Auth
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/refresh",
            // OTP - Quen mat khau
            "/api/auth/forgot-password",
            "/api/auth/reset-password",
            // OTP - Xac thuc email
            "/api/auth/send-verify-email",
            "/api/auth/verify-email",
            // Swagger / OpenAPI
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            // WebSocket (for chat)
            "/ws/**",
            // Static resources / pings
            "/actuator/**",
            "/error"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)

                .cors(cors ->
                        cors.configurationSource(corsConfigurationSource())
                )

                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                .exceptionHandling(exception ->
                        exception.authenticationEntryPoint(jwtAuthenticationEntryPoint)
                )

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/payments/vnpay/ipn", "/api/payments/vnpay/return").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/listings/mine", "/api/listings/pending", "/api/listings/admin").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/listings", "/api/listings/{id}").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/room-types", "/api/room-types/filter",
                                "/api/amenities", "/api/amenities/filter", "/api/provinces", "/api/wards").permitAll()
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated()
                )

                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration = new CorsConfiguration();

        // Allow dev frontends (Angular FE, etc.)
        configuration.setAllowedOrigins(
                List.of(
                        "http://localhost:4200",
                        "http://localhost:3000",
                        "http://localhost:5173"
                )
        );

        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
        );

        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
