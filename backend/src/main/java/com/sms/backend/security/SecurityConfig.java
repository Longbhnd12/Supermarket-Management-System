package com.sms.backend.security;

import com.sms.backend.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Authentication = JWT in an HttpOnly cookie, stateless (SDD: "JWT filter chain, token provider,
 * password encoder, stateless session"). There is no HttpSession: every request is authenticated
 * by JwtAuthenticationFilter. Because the token travels in a cookie, CSRF protection is ON
 * (double-submit cookie XSRF-TOKEN / header X-XSRF-TOKEN). See docs/authentication.md.
 */
@Configuration
public class SecurityConfig {

    /** Public endpoints: no login needed, and no CSRF token needed (they do not use the auth cookie). */
    static final String[] PUBLIC_AUTH_ENDPOINTS = {
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtTokenProvider tokenProvider,
                                                   AuthCookieService cookieService,
                                                   AuthUserLoader userLoader) throws Exception {
        RestSecurityErrorHandler errorHandler = new RestSecurityErrorHandler();
        // One repository shared by Spring Security and the JWT filter (see JwtAuthenticationFilter)
        SecurityContextRepository contextRepository = new RequestAttributeSecurityContextRepository();

        http
                // CSRF: the token is readable by our frontend (XSRF-TOKEN cookie) and must be echoed
                // in the X-XSRF-TOKEN header; the plain handler accepts the raw cookie value.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers(PUBLIC_AUTH_ENDPOINTS))
                .cors(cors -> {
                })
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(context -> context.securityContextRepository(contextRepository))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/health", "/api/v1/test-error").permitAll()
                        .requestMatchers(PUBLIC_AUTH_ENDPOINTS).permitAll()
                        // Logout works even with an expired token: it only clears the cookie
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").permitAll()
                        .requestMatchers("/api/v1/system-settings/**", "/api/v1/system-logs/**").hasRole("ADMIN")
                        // UC04/UC05/UC06 - permission based (role.permissions, see RolePermissions)
                        // GET /api/v1/users (plain list) stays open: the POS screen uses it to load cashiers
                        .requestMatchers(HttpMethod.GET, "/api/v1/users").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/**").hasAuthority("PERM_USER_VIEW")
                        .requestMatchers("/api/v1/users/**").hasAuthority("PERM_USER_MANAGE")
                        .requestMatchers("/api/v1/roles/**").hasAuthority("PERM_ROLE_MANAGE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/invoices", "/api/v1/invoices/**",
                                "/api/v1/sales/*/invoice", "/api/v1/sales/*/invoice/pdf")
                        .access(org.springframework.security.authorization.AuthorizationManagers.allOf(
                                org.springframework.security.authorization.AuthorityAuthorizationManager.hasAnyRole("ADMIN", "MANAGER", "CASHIER"),
                                org.springframework.security.authorization.AuthorityAuthorizationManager.hasAuthority("PERM_INVOICE_VIEW")))
                        .anyRequest().authenticated()
                )
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .addFilterBefore(new JwtAuthenticationFilter(tokenProvider, cookieService, userLoader, contextRepository),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(
                List.of("http://localhost:5173")
        );

        config.setAllowedMethods(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        );

        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/api/**", config);

        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Used by Spring Security internals; the login itself is done by AuthServiceImpl. */
    @Bean
    public UserDetailsService userDetailsService(UserRepository userRepository) {
        return identifier -> userRepository.findByUsername(identifier.trim())
                .or(() -> userRepository.findByEmail(identifier.trim()))
                .map(SecurityUserMapper::toUserDetails)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}
