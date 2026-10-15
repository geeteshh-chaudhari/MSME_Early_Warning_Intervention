package com.msme.security;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            CustomUserDetailsService userDetailsService) {

        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        /*
         * If there is no Bearer token, continue.
         * Spring Security authorization rules will later decide
         * whether the requested endpoint is public or protected.
         */
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = authHeader.substring(7);

        try {

            /*
             * extractUserId() also verifies:
             * - JWT signature
             * - issuer
             * - expiration
             */
            Long userId = jwtService.extractUserId(jwt);

            if (SecurityContextHolder.getContext()
                    .getAuthentication() == null) {

                /*
                 * Load the latest user state from Oracle.
                 *
                 * This is useful because a previously issued token
                 * should stop working if the account is deactivated.
                 */
                UserDetails userDetails =
                        userDetailsService.loadUserById(userId);

                if (userDetails instanceof CustomUserPrincipal principal
                        && principal.isEnabled()
                        && jwtService.isTokenValid(jwt, principal)) {

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    principal,
                                    null,
                                    principal.getAuthorities()
                            );

                    authentication.setDetails(
                            new org.springframework.security.web
                                    .authentication.WebAuthenticationDetailsSource()
                                    .buildDetails(request)
                    );

                    SecurityContextHolder.getContext()
                            .setAuthentication(authentication);
                }
            }

        } catch (RuntimeException ex) {

            /*
             * Invalid, expired, malformed token, unknown user, etc.
             *
             * Do not authenticate the request.
             * For protected endpoints Spring Security will return 401.
             */
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}