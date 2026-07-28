package com.aquatrack.smartwaterbilling.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 6 configuration.
 * <ul>
 *   <li>Stateless (JWT-based), no CSRF needed.</li>
 *   <li>{@code /api/auth/**} is public.</li>
 *   <li>{@code /api/test} is public (architecture connectivity test).</li>
 *   <li>{@code /swagger-ui/**}, {@code /swagger-ui.html} are public (Swagger UI).</li>
 *   <li>{@code /v3/api-docs/**}, {@code /v3/api-docs.yaml} are public (OpenAPI spec).</li>
 *   <li>All other routes require authentication.</li>
 *   <li>Fine-grained access control handled per-method with {@code @PreAuthorize}.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserDetailsServiceImpl userDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // Enable CORS — picks up the CorsConfigurationSource bean below
                .cors(Customizer.withDefaults())
                // Disable CSRF for stateless REST API
                .csrf(AbstractHttpConfigurer::disable)
                // Stateless — no session
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Authorization rules
                .authorizeHttpRequests(auth -> auth
                        // Allow all CORS preflight requests
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Public auth endpoints
                        .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                        // Public test/health endpoint
                        .requestMatchers("/api/test").permitAll()
                        // Swagger UI — must be before the authenticated() catch-all
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // OpenAPI spec endpoints
                        .requestMatchers("/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()
                        // All other requests require authentication;
                        // fine-grained ADMIN/RESIDENT checks are done via @PreAuthorize
                        .anyRequest().authenticated())
                // Register JWT filter before Spring's default username/password filter
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * CORS configuration source — used by Spring Security's CORS filter.
     * Allows the Vite dev server origin to make cross-origin requests.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
