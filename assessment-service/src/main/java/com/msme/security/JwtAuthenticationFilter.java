package com.msme.security;

import java.io.IOException;

import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AssessmentUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            AssessmentUserDetailsService userDetailsService) {

        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String authorization = request.getHeader("Authorization");

        if (authorization == null
                || !authorization.startsWith("Bearer ")) {

            filterChain.doFilter(request, response);
            return;
        }

        String token = authorization.substring(7).trim();

        try {
            Claims claims =
                    jwtService.validateAndExtractClaims(token);

            Long userId = Long.valueOf(claims.getSubject());

            CustomUserPrincipal principal =
                    userDetailsService.loadUserById(userId);

            String tokenRole =
                    claims.get("role", String.class);

            if (!principal.isEnabled()
                    || !principal.getRole().equals(tokenRole)) {

                throw new BadCredentialsException(
                        "Invalid authenticated user"
                );
            }

            var authentication =
                    new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            principal.getAuthorities()
                    );

            authentication.setDetails(
                    new WebAuthenticationDetailsSource()
                            .buildDetails(request)
            );

            var context =
                    SecurityContextHolder.createEmptyContext();

            context.setAuthentication(authentication);

            SecurityContextHolder.setContext(context);

        } catch (DataAccessException ex) {

            SecurityContextHolder.clearContext();

            response.sendError(
                    HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Authentication data is unavailable"
            );

            return;

        } catch (JwtException
                | IllegalArgumentException
                | AuthenticationException ex) {

            SecurityContextHolder.clearContext();

            response.sendError(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Invalid or expired authentication token"
            );

            return;
        }

        filterChain.doFilter(request, response);
    }
}