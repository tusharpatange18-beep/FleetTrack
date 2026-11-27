package com.fleettrack.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs once for every request. If the request has a valid "Authorization: Bearer <token>"
 * header, it tells Spring Security who the user is. If not, it does nothing, and the security
 * rules in SecurityConfig then answer 401 for protected URLs.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // No token: carry on without logging anybody in
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        // The token is the text after "Bearer "
        String jwt = authHeader.substring(BEARER_PREFIX.length());
        try {
            String username = jwtService.extractUsername(jwt);
            boolean notLoggedInYet = SecurityContextHolder.getContext().getAuthentication() == null;

            if (username != null && notLoggedInYet) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                if (jwtService.isTokenValid(jwt, userDetails)) {
                    // Valid token: tell Spring Security "this request is from this user, with these roles"
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,                          // no password needed, the token proved who they are
                            userDetails.getAuthorities()   // the user's role, e.g. ADMIN
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            // Fake, broken or expired token: stay logged out; SecurityConfig will answer 401/403
        }

        // Pass the request on to the next filter (and finally to the controller)
        filterChain.doFilter(request, response);
    }
}
