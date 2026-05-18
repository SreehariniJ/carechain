package com.carechain.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private JwtFilter jwtFilter;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${spring.h2.console.enabled:false}")
    private boolean h2ConsoleEnabled;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .headers(headers -> {
                if (h2ConsoleEnabled) {
                    headers.frameOptions(frame -> frame.sameOrigin());
                } else {
                    headers.frameOptions(frame -> frame.deny());
                }
                headers.contentTypeOptions(Customizer.withDefaults());
                headers.referrerPolicy(referrer -> referrer
                        .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
                headers.permissionsPolicy(policy -> policy
                        .policy("camera=(), geolocation=(), microphone=()"));
                headers.httpStrictTransportSecurity(hsts -> hsts
                        .includeSubDomains(true)
                        .preload(true)
                        .maxAgeInSeconds(31536000));
                headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; " +
                                "script-src 'self' 'unsafe-inline'; " +
                                "style-src 'self' 'unsafe-inline'; " +
                                "img-src 'self' data: blob:; " +
                                "font-src 'self' data:; " +
                                "connect-src 'self' ws: wss:; " +
                                "object-src 'none'; " +
                                "base-uri 'self'; " +
                                "frame-ancestors 'self'; " +
                                "form-action 'self'"));
            })
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers("/api/auth/**").permitAll();
                auth.requestMatchers("/login", "/register", "/").permitAll();
                auth.requestMatchers("/beds/availability", "/api/beds/availability").permitAll();
                auth.requestMatchers("/ws/**", "/ws").permitAll();
                auth.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll();
                auth.requestMatchers("/error").permitAll();
                if (h2ConsoleEnabled) {
                    auth.requestMatchers("/h2-console/**").permitAll();
                }
                auth.requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**").permitAll();
                auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                auth.requestMatchers("/api/admin/**").hasRole("ADMIN");
                auth.requestMatchers("/dashboard/admin").hasRole("ADMIN");
                auth.requestMatchers("/dashboard/doctor").hasRole("DOCTOR");
                auth.requestMatchers("/dashboard/patient").hasRole("PATIENT");
                auth.anyRequest().authenticated();
            })
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    if (request.getRequestURI().startsWith("/api/")) {
                        response.setStatus(401);
                        response.setContentType("application/json");
                        objectMapper.writeValue(response.getWriter(),
                                ApiErrorResponses.body(HttpStatus.UNAUTHORIZED, "Unauthorized", request.getRequestURI()));
                    } else {
                        response.sendRedirect("/login");
                    }
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    if (request.getRequestURI().startsWith("/api/")) {
                        response.setStatus(403);
                        response.setContentType("application/json");
                        objectMapper.writeValue(response.getWriter(),
                                ApiErrorResponses.body(HttpStatus.FORBIDDEN, "Access Denied", request.getRequestURI()));
                    } else {
                        response.sendRedirect("/login");
                    }
                })
            );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
