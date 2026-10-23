package com.msme.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.msme.exception.FinancialServiceUnavailableException;
import com.msme.exception.InvalidRequestException;
import com.msme.exception.MsmeServiceException;

@Component
public class MsmeAccessClient {

    private final RestClient restClient;

    public MsmeAccessClient(
            @Qualifier("financeRestClient")
            RestClient restClient) {

        this.restClient = restClient;
    }

    public void checkAccess(Long msmeId) {

        if (msmeId == null || msmeId <= 0) {
            throw new InvalidRequestException(
                    "Valid MSME ID is required"
            );
        }

        try {
            var response = restClient.get()
                    .uri("/api/msmes/{msmeId}", msmeId)
                    .retrieve()
                    .toBodilessEntity();

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new MsmeServiceException(
                        HttpStatus.BAD_GATEWAY,
                        "Unexpected response from MS1"
                );
            }

        } catch (MsmeServiceException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new FinancialServiceUnavailableException(
                    "MS1 is unavailable",
                    ex
            );
        }
    }
}