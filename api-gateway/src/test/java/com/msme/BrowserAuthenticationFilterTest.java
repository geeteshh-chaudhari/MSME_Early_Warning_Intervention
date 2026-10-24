package com.msme;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import java.util.concurrent.atomic.AtomicReference;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;
class BrowserAuthenticationFilterTest {
    private final BrowserAuthenticationFilter filter=new BrowserAuthenticationFilter();
    @Test void cookieIsForwardedAsBearerForExistingServiceChecks() {
        var exchange=MockServerWebExchange.from(MockServerHttpRequest.get("/api/assessment/assessments/1")
                .cookie(new HttpCookie("MSME_AUTH","signed-token")));
        var header=new AtomicReference<String>();
        filter.filter(exchange,next->{header.set(next.getRequest().getHeaders().getFirst("Authorization"));return Mono.empty();}).block();
        assertEquals("Bearer signed-token",header.get());
    }
    @Test void cookieWritesRequireMatchingCsrfHeader() {
        var exchange=MockServerWebExchange.from(MockServerHttpRequest.post("/api/warnings/1/decisions")
                .cookie(new HttpCookie("MSME_AUTH","token"),new HttpCookie("MSME_CSRF","expected")));
        filter.filter(exchange,next->{fail("Unverified write reached a service");return Mono.empty();}).block();
        assertEquals(HttpStatus.FORBIDDEN,exchange.getResponse().getStatusCode());
    }
    @Test void verifiedCookieWriteIsForwarded() {
        var exchange=MockServerWebExchange.from(MockServerHttpRequest.post("/api/recommendations")
                .cookie(new HttpCookie("MSME_AUTH","token"),new HttpCookie("MSME_CSRF","expected"))
                .header("X-CSRF-Token","expected"));
        var header=new AtomicReference<String>();
        filter.filter(exchange,next->{header.set(next.getRequest().getHeaders().getFirst("Authorization"));return Mono.empty();}).block();
        assertEquals("Bearer token",header.get());
    }
    @Test void bearerClientsWithoutCookiesKeepExistingContract() {
        var exchange=MockServerWebExchange.from(MockServerHttpRequest.post("/api/recommendations").header("Authorization","Bearer cli-token"));
        var header=new AtomicReference<String>();
        filter.filter(exchange,next->{header.set(next.getRequest().getHeaders().getFirst("Authorization"));return Mono.empty();}).block();
        assertEquals("Bearer cli-token",header.get());
    }
}
