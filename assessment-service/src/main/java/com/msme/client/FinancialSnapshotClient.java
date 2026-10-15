
package com.msme.client;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.msme.dto.assessment.FinancialSnapshotRequest;
import com.msme.exception.FinancialServiceUnavailableException;

@Component
public class FinancialSnapshotClient {

    private final RestClient restClient;

    public FinancialSnapshotClient(
            @Qualifier("financeRestClient")
            RestClient restClient) {

        this.restClient = restClient;
    }

    public List<FinancialSnapshotRequest> getLatestThreeSnapshots(
            Long msmeId) {

        if (msmeId == null || msmeId <= 0) {
            throw new IllegalArgumentException(
                    "Valid MSME ID is required");
        }

        try {

            FinancialSnapshotRequest[] response =
                    restClient.get()
                        .uri(
                            "/api/msmes/{msmeId}/financial-snapshots/latest-three",
                            msmeId
                        )
                        .retrieve()
                        .onStatus(
                            HttpStatusCode::isError,
                            (request, result) -> {
                                throw new FinancialServiceUnavailableException(
                                    "MS1 returned HTTP "
                                    + result.getStatusCode()
                                );
                            }
                        )
                        .body(FinancialSnapshotRequest[].class);

            if (response == null || response.length != 3) {
                throw new FinancialServiceUnavailableException(
                    "MS1 must return exactly three monthly snapshots"
                );
            }

            List<FinancialSnapshotRequest> snapshots =
                    List.of(response);

            for (FinancialSnapshotRequest snapshot : snapshots) {

                if (snapshot == null
                        || snapshot.msmeId() == null
                        || !msmeId.equals(snapshot.msmeId())) {

                    throw new FinancialServiceUnavailableException(
                        "MS1 returned invalid MSME snapshot data"
                    );
                }
            }

            return snapshots;

        } catch (FinancialServiceUnavailableException ex) {
            throw ex;

        } catch (RestClientException ex) {
            throw new FinancialServiceUnavailableException(
                "Financial Data Service is unavailable",
                ex
            );
        }
    }
}
