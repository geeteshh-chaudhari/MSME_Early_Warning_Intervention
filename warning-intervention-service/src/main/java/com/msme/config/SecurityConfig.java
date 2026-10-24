package com.msme.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;

import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;

import com.msme.security.CustomUserDetailsService;
import com.msme.security.JwtAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(
            CustomUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {

        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);

        provider.setPasswordEncoder(passwordEncoder);

        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            DaoAuthenticationProvider authenticationProvider) {

        return new ProviderManager(authenticationProvider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            DaoAuthenticationProvider authenticationProvider,
            JwtAuthenticationFilter jwtAuthenticationFilter)
            throws Exception {

        AccessDeniedHandlerImpl accessDeniedHandler =
                new AccessDeniedHandlerImpl();

        http
            /*
             * REST API + JWT:
             * no browser session and no server-side CSRF token required.
             */
            .csrf(csrf -> csrf.disable())

            /*
             * JWT authentication is stateless.
             */
            .sessionManagement(session ->
                session.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS
                )
            )

            /*
             * Authentication provider used by the login flow.
             */
            .authenticationProvider(authenticationProvider)

            /*
             * Endpoint authorization rules.
             */
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/internal/warnings/**").permitAll()
                .requestMatchers("/v3/api-docs", "/v3/api-docs/**").permitAll()
                .requestMatchers(
                    
                    "/actuator/health",
                    "/error"
                ).permitAll()

                .anyRequest().authenticated()
            )

            /*
             * Return 401 when authentication is missing/invalid
             * and 403 when an authenticated user is not permitted.
             */
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)
                )
                .accessDeniedHandler(accessDeniedHandler)
            )

            /*
             * We are not using HTTP Basic authentication.
             */
            .httpBasic(basic -> basic.disable())

            /*
             * We are not using Spring's HTML login form.
             */
            .formLogin(form -> form.disable())

            /*
             * JWT filter executes before Spring's username/password filter.
             */
            .addFilterBefore(
                jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }

    /*
     * JwtAuthenticationFilter is already added explicitly to the
     * Spring Security filter chain above.
     *
     * Because it is also a Spring @Component, Boot could otherwise
     * register it as a normal servlet filter as well.
     *
     * Disable that additional registration so it executes only once.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter>
            jwtFilterRegistration(JwtAuthenticationFilter filter) {

        FilterRegistrationBean<JwtAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);

        registration.setEnabled(false);

        return registration;
    }
}
