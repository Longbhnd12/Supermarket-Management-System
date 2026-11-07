package com.sms.backend.security;

import com.sms.backend.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
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
 * (double-submit cookie XSRF-TOKEN / header X-XSRF-TOKEN).
 * Authorization (which endpoint needs which permission): ApiPermissionRules, backend/API-PERMISSIONS.md.
 */
@Configuration
public class SecurityConfig {

    /** Public POST endpoints that need no CSRF token (they do not use the auth cookie). Access rules: ApiPermissionRules. */
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
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(context -> context.securityContextRepository(contextRepository))
                // Every endpoint -> permission: see ApiPermissionRules (first match wins, deny by default)
                .authorizeHttpRequests(SecurityConfig::applyApiPermissionRules)
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

    /**
     * Turns the ApiPermissionRules table into Spring Security rules, in the same order.
     * PERMISSION rule = has one of the roles (if any) AND one of the permissions (if any).
     * A request that matches no rule is denied (401 when not logged in, 403 otherwise).
     */
    static void applyApiPermissionRules(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
        for (ApiPermissionRules.Rule rule : ApiPermissionRules.RULES) {
            var url = rule.method() == null
                    ? auth.requestMatchers(rule.pattern())
                    : auth.requestMatchers(rule.method(), rule.pattern());
            switch (rule.access()) {
                case PUBLIC -> url.permitAll();
                case AUTHENTICATED -> url.authenticated();
                case PERMISSION -> {
                    if (rule.roles().isEmpty()) {
                        url.hasAnyAuthority(rule.permissionAuthorities());
                    } else if (rule.anyOf().isEmpty()) {
                        url.hasAnyAuthority(rule.roleAuthorities());
                    } else {
                        url.access(AuthorizationManagers.allOf(
                                AuthorityAuthorizationManager.hasAnyAuthority(rule.roleAuthorities()),
                                AuthorityAuthorizationManager.hasAnyAuthority(rule.permissionAuthorities())));
                    }
                }
            }
        }
        auth.anyRequest().denyAll();
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
