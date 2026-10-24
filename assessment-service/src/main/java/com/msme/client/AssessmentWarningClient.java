package com.msme.client;

import java.time.Duration;
import com.msme.exception.FinancialServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Sends only a committed assessment ID; the warning service reads its evidence. */
@Component
public class AssessmentWarningClient {
    private final RestClient client;

    public AssessmentWarningClient(@Value("${warning.base-url:http://localhost:8084}") String url,
            @Value("${app.internal.token}") String token) {
        var factory = new JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(15));
        client = RestClient.builder().baseUrl(url).requestFactory(factory)
                .defaultHeader("X-Internal-Token", token).build();
    }

    public void process(Long assessmentId) {
        try {
            client.post().uri("/internal/warnings/assessments/{id}", assessmentId)
                    .retrieve().toBodilessEntity();
        } catch (org.springframework.web.client.RestClientException ex) {
            throw new FinancialServiceUnavailableException(
                    "Assessment saved, but warning processing is unavailable. Run this assessment again "
                    + "to retry warning processing; its saved result will not be recalculated.", ex);
        }
    }
}
