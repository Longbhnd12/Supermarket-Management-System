package com.sms.backend.security;

import com.sms.backend.security.JwtTokenProvider.IssuedToken;
import com.sms.backend.security.JwtTokenProvider.TokenClaims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Reads the JWT from the HttpOnly cookie on every request and sets the SecurityContext (stateless).
 * Invalid / expired / invalidated tokens, and inactive accounts, leave the request anonymous and
 * clear the cookie; protected endpoints then answer 401 (see RestSecurityErrorHandler).
 * Created in SecurityConfig (not a @Component, so Spring Boot does not register it twice).
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final AuthCookieService cookieService;
    private final AuthUserLoader userLoader;
    private final SecurityContextRepository contextRepository;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, AuthCookieService cookieService,
                                   AuthUserLoader userLoader, SecurityContextRepository contextRepository) {
        this.tokenProvider = tokenProvider;
        this.cookieService = cookieService;
        this.userLoader = userLoader;
        this.contextRepository = contextRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<String> token = cookieService.readToken(request);
        if (token.isPresent() && SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(token.get(), request, response);
        }
        chain.doFilter(request, response);
    }

    private void authenticate(String token, HttpServletRequest request, HttpServletResponse response) {
        Optional<TokenClaims> claims = tokenProvider.parse(token);
        if (claims.isEmpty()) {
            cookieService.clear(response);
            return;
        }
        Optional<UserDetails> principal = userLoader.loadActiveUser(claims.get().userId());
        if (principal.isEmpty()) {
            cookieService.clear(response);
            return;
        }

        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal.get(), null, principal.get().getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        // Must be saved to the repository too. Otherwise SessionManagementFilter thinks the user
        // "just logged in" on every request and runs CsrfAuthenticationStrategy, which deletes the
        // XSRF-TOKEN cookie -> the next POST/PUT fails with CSRF_INVALID (bug: first click fails).
        contextRepository.saveContext(context, request, response);

        // Sliding expiration: an active user is not logged out in the middle of their work
        if (tokenProvider.shouldRenew(claims.get()) && !isLogout(request)) {
            String role = principal.get().getAuthorities().stream()
                    .map(authority -> authority.getAuthority())
                    .filter(name -> name.startsWith("ROLE_"))
                    .map(name -> name.substring("ROLE_".length()))
                    .findFirst()
                    .orElse("");
            IssuedToken renewed = tokenProvider.issue(claims.get().userId(), principal.get().getUsername(), role);
            cookieService.writeToken(response, renewed.token(), renewed.lifetime());
        }
    }

    private boolean isLogout(HttpServletRequest request) {
        return request.getRequestURI() != null && request.getRequestURI().endsWith("/auth/logout");
    }
}
