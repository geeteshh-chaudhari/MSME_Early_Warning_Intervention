package com.msme.config;

import java.io.IOException;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.msme.exception.FinancialServiceUnavailableException;
import com.msme.exception.MsmeServiceException;
import com.msme.security.CustomUserPrincipal;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    public RestClient financeRestClient(
            RestClient.Builder builder,
            @Value("${msme.finance.base-url}") String baseUrl,
            @Value("${msme.finance.connect-timeout-ms:3000}")
            int connectTimeoutMs,
            @Value("${msme.finance.read-timeout-ms:5000}")
            int readTimeoutMs) {

        java.net.http.HttpClient httpClient =
                java.net.http.HttpClient.newBuilder()
                        .connectTimeout(
                                Duration.ofMillis(connectTimeoutMs)
                        )
                        .followRedirects(
                                java.net.http.HttpClient.Redirect.NEVER
                        )
                        .build();

        var factory =
                new JdkClientHttpRequestFactory(httpClient);

        factory.setReadTimeout(
                Duration.ofMillis(readTimeoutMs)
        );

        return builder.clone()
                .baseUrl(baseUrl)
                .requestFactory(factory)

                .requestInterceptor((request, body, execution) -> {

                    var authentication =
                            SecurityContextHolder.getContext()
                                    .getAuthentication();

                    if (authentication == null
                            || !authentication.isAuthenticated()
                            || !(authentication.getPrincipal()
                                instanceof CustomUserPrincipal)) {

                        throw new AuthenticationCredentialsNotFoundException(
                                "JWT authentication is required"
                        );
                    }

                    if (!(RequestContextHolder.getRequestAttributes()
                            instanceof ServletRequestAttributes attributes)) {

                        throw new AuthenticationCredentialsNotFoundException(
                                "Original authenticated request is unavailable"
                        );
                    }

                    String authorization = attributes.getRequest()
                            .getHeader(HttpHeaders.AUTHORIZATION);

                    if (authorization == null
                            || !authorization.startsWith("Bearer ")
                            || authorization.substring(7).isBlank()) {

                        throw new AuthenticationCredentialsNotFoundException(
                                "Bearer token is required"
                        );
                    }

                    request.getHeaders().set(
                            HttpHeaders.AUTHORIZATION,
                            authorization
                    );

                    try {
                        return execution.execute(request, body);

                    } catch (IOException ex) {
                        throw new FinancialServiceUnavailableException(
                                "MS1 is unavailable",
                                ex
                        );
                    }
                })

                .defaultStatusHandler(
                        HttpStatusCode::isError,
                        (request, response) -> {
                            throw MsmeServiceException.fromUpstream(
                                    response.getStatusCode().value()
                            );
                        }
                )

                .build();
    }
}