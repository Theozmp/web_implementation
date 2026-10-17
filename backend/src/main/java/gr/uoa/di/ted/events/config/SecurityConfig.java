package gr.uoa.di.ted.events.config;

import gr.uoa.di.ted.events.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
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
 * Stateless JWT security configuration.
 *
 * Public endpoints: POST /auth/register and /auth/login, GET /health,
 * GET /events/** (public browsing) and /categories, and the Swagger/OpenAPI docs.
 *
 * /auth/change-password isn't public: it needs a valid JWT, but
 * JwtAuthenticationFilter lets it through even when mustChangePassword is true,
 * since that's the whole point.
 *
 * @EnableMethodSecurity turns on @PreAuthorize so role checks live on the
 * controllers instead of being duplicated in the filter chain. CORS only matters
 * in dev (Angular on :4200); in production nginx serves the frontend and /api on
 * one origin.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)     // stateless API, no CSRF token needed
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // register and login need no credentials
                .requestMatchers(HttpMethod.POST,
                        "/api/v1/auth/register",
                        "/api/v1/auth/login").permitAll()
                // infrastructure / docs
                .requestMatchers("/api/v1/health").permitAll()
                .requestMatchers("/actuator/health").permitAll()   // liveness probe
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // bookings need auth even though GET /events/** is public; these must
                // come before the permitAll wildcard below (first match wins).
                .requestMatchers(HttpMethod.POST, "/api/v1/events/*/bookings").authenticated()
                .requestMatchers(HttpMethod.GET,  "/api/v1/events/*/bookings").authenticated()
                .requestMatchers("/api/v1/bookings/**").authenticated()
                // messaging always requires auth
                .requestMatchers("/api/v1/messages/**").authenticated()
                // public read-only browsing (unauthenticated visitor)
                .requestMatchers(HttpMethod.GET, "/api/v1/events/**", "/api/v1/categories").permitAll()
                // everything else needs a valid JWT
                .anyRequest().authenticated()
            )
            // JWT filter runs before Spring's username/password filter
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        // Angular dev server; in production it's same-origin via nginx, so this is a no-op
        cfg.setAllowedOrigins(List.of("http://localhost:4200", "http://localhost"));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }

    /**
     * BCrypt strength 12 (~250ms per hash on modern hardware), deliberately slow to
     * make brute force impractical.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Exposes the AuthenticationManager for future flows (e.g. OAuth2). Not used
     * today: AuthService checks credentials itself to give better error messages
     * than Spring's default exceptions.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg)
            throws Exception {
        return cfg.getAuthenticationManager();
    }
}
