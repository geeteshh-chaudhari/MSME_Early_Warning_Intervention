package com.msme;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false"
})
class SwaggerDocumentationTests {
    static final HttpServer upstream = startUpstream();
    @LocalServerPort int port;
    static HttpServer startUpstream() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            for (String path : new String[]{"/v3/api-docs", "/openapi.json"}) {
                server.createContext(path, exchange -> {
                    byte[] body = ("{\"openapi\":\"3.1.0\",\"info\":{\"title\":\"fixture\",\"version\":\"1\"},"
                        + "\"paths\":{},\"x-upstream-path\":\"" + exchange.getRequestURI().getPath() + "\"}").getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    try (var stream = exchange.getResponseBody()) { stream.write(body); }
                });
            }
            server.start(); return server;
        } catch (IOException ex) { throw new IllegalStateException(ex); }
    }
    @DynamicPropertySource static void routes(DynamicPropertyRegistry properties) {
        // Spring replaces indexed lists across property sources: supply the complete
        // production route list, changing only documentation destinations to a fixture.
        var routes = new java.util.Properties();
        try (var stream = SwaggerDocumentationTests.class.getResourceAsStream("/application.properties")) {
            routes.load(stream);
        } catch (IOException ex) { throw new IllegalStateException(ex); }
        for (String key : routes.stringPropertyNames()) {
            if (!key.startsWith("spring.cloud.gateway.server.webflux.routes[")) continue;
            boolean fixture = key.matches(".*routes\\[[5-9]\\]\\.uri");
            properties.add(key, () -> fixture ? "http://127.0.0.1:" + upstream.getAddress().getPort() : routes.getProperty(key));
        }
    }
    @AfterAll static void stop() { upstream.stop(0); }
    WebTestClient client() { return WebTestClient.bindToServer().baseUrl("http://localhost:"+port).build(); }
    @Test void swaggerListsFiveServicesAndServesItsAssets() {
        client().get().uri("/v3/api-docs/swagger-config").exchange().expectStatus().isOk()
            .expectBody().jsonPath("$.urls.length()").isEqualTo(5)
            .jsonPath("$.urls[*].url").value(urls -> assertTrue(((java.util.List<?>) urls).contains("/openapi/msme")))
            .jsonPath("$['urls.primaryName']").isEqualTo("MSME");
        client().get().uri("/swagger-ui.html").exchange().expectStatus().is3xxRedirection();
        client().get().uri("/swagger-ui/index.html").exchange().expectStatus().isOk();
    }
    @Test void allFiveSpecificationsAreProxiedWithCorrectPathRewriting() {
        for (String service : new String[]{"msme","assessment","warning","recommendation","dashboard"}) {
            String expected = service.equals("recommendation") || service.equals("dashboard") ? "/openapi.json" : "/v3/api-docs";
            client().get().uri("/openapi/"+service).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.x-upstream-path").isEqualTo(expected);
        }
    }
}
