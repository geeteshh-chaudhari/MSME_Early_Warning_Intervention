package com.msme;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class BrowserAuthenticationFilter implements WebFilter, Ordered {
    @Override public int getOrder() { return -100; }
    @Override public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var request=exchange.getRequest();
        String path=request.getPath().value();
        if (!path.startsWith("/api/")) return chain.filter(exchange);
        var token=request.getCookies().getFirst("MSME_AUTH");
        if (token == null) return chain.filter(exchange);
        if (!Set.of("GET","HEAD","OPTIONS").contains(request.getMethod().name())) {
            var csrf=request.getCookies().getFirst("MSME_CSRF");
            String supplied=request.getHeaders().getFirst("X-CSRF-Token");
            if (csrf == null || supplied == null || !MessageDigest.isEqual(
                    csrf.getValue().getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8))) {
                var response=exchange.getResponse();
                response.setStatusCode(HttpStatus.FORBIDDEN);
                response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                return response.writeWith(Mono.just(response.bufferFactory().wrap(
                        "{\"message\":\"CSRF verification failed\"}".getBytes(StandardCharsets.UTF_8))));
            }
        }
        if (path.equals("/api/auth/login") || path.equals("/api/auth/logout")
                || request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION) != null) return chain.filter(exchange);
        return chain.filter(exchange.mutate().request(request.mutate()
                .headers(headers -> headers.setBearerAuth(token.getValue())).build()).build());
    }
}
