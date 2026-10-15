
package com.msme.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    public RestClient financeRestClient(
            RestClient.Builder builder,

            @Value("${msme.finance.base-url}")
            String baseUrl,

            @Value("${msme.finance.connect-timeout-ms:3000}")
            int connectTimeoutMs,

            @Value("${msme.finance.read-timeout-ms:5000}")
            int readTimeoutMs) {

        java.net.http.HttpClient httpClient =
                java.net.http.HttpClient.newBuilder()
                    .connectTimeout(
                        Duration.ofMillis(connectTimeoutMs)
                    )
                    .build();

        JdkClientHttpRequestFactory factory =
                new JdkClientHttpRequestFactory(httpClient);

        factory.setReadTimeout(
            Duration.ofMillis(readTimeoutMs)
        );

        return builder
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}
