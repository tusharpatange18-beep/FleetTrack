package com.fleettrack.config;

import com.fleettrack.security.JwtAuthenticationFilter;
import com.fleettrack.security.RestSecurityErrorHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String ADMIN = "ADMIN";
    private static final String DISPATCHER = "DISPATCHER";

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;
    private final RestSecurityErrorHandler securityErrorHandler;
    private final FleetProperties properties;

    /**
     * The security rules for every URL. Spring Security's builder style takes small lambdas
     * ("auth -> auth...."); each one just means "configure this part like so".
     * Rules are checked top to bottom, and the first rule that matches the URL wins.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Stateless JWT API: no cookies, so CSRF protection is not needed
            .csrf(AbstractHttpConfigurer::disable)
            // Which websites may call this API from a browser (see corsConfigurationSource below)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() // CORS preflight
                // Open to everyone: login/register, health check, API docs
                .requestMatchers("/api/auth/**", "/error").permitAll()
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                // Drivers are managed by admins; dispatchers may read them to plan assignments
                .requestMatchers(HttpMethod.GET, "/api/drivers/**").hasAnyAuthority(ADMIN, DISPATCHER)
                .requestMatchers("/api/drivers/**").hasAuthority(ADMIN)
                // Everything else in the API: admins and dispatchers
                .requestMatchers("/api/vehicles/**", "/api/deliveries/**", "/api/customers/**", "/api/dashboard/**")
                    .hasAnyAuthority(ADMIN, DISPATCHER)
                // Any other URL: must at least be logged in
                .anyRequest().authenticated()
            )
            // No server-side session: every request must bring its own JWT
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Our JSON error bodies for 401 (not logged in) and 403 (wrong role)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(securityErrorHandler)
                .accessDeniedHandler(securityErrorHandler))
            .authenticationProvider(authenticationProvider())
            // Read the JWT before Spring's normal username/password filter runs
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** Only origins listed in fleettrack.cors-allowed-origins may call the API from a browser. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        // Skip empty entries (e.g. an empty environment variable)
        List<String> allowedOrigins = new ArrayList<>();
        for (String origin : properties.corsAllowedOrigins()) {
            if (!origin.isBlank()) {
                allowedOrigins.add(origin);
            }
        }

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    /** Tells Spring Security how to find users (our database) and how passwords are hashed (BCrypt). */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /** BCrypt: a slow, salted hash, so stolen hashes are very hard to turn back into passwords. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
