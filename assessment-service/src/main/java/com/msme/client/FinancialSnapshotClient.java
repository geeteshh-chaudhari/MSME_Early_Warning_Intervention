package com.msme.client;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.msme.dto.assessment.AssessmentInputResponse;
import com.msme.dto.assessment.FinancialSnapshotRequest;
import com.msme.exception.FinancialServiceUnavailableException;
import com.msme.exception.InvalidRequestException;
import com.msme.exception.MsmeServiceException;

import jakarta.validation.Validator;

@Component
public class FinancialSnapshotClient {

    private final RestClient restClient;
    private final Validator validator;

    public FinancialSnapshotClient(
            @Qualifier("financeRestClient") RestClient restClient,
            Validator validator) {

        this.restClient = restClient;
        this.validator = validator;
    }

    public List<FinancialSnapshotRequest> getAssessmentSnapshots(
            Long msmeId,
            LocalDate reportingPeriod) {

        if (msmeId == null || msmeId <= 0) {
            throw new InvalidRequestException(
                    "Valid MSME ID is required"
            );
        }

        if (reportingPeriod == null
                || reportingPeriod.getDayOfMonth() != 1) {

            throw new InvalidRequestException(
                    "Reporting period must be the first day of a month"
            );
        }

        AssessmentInputResponse input;

        try {
            input = restClient.get()
                    .uri(
                            "/api/assessment-data/msmes/{msmeId}"
                            + "/input/{reportingPeriod}",
                            msmeId,
                            reportingPeriod
                    )
                    .retrieve()
                    .body(AssessmentInputResponse.class);

        } catch (RestClientException ex) {
            throw new FinancialServiceUnavailableException(
                    "MS1 assessment input could not be retrieved",
                    ex
            );
        }

        validateInput(input, msmeId, reportingPeriod);

        return input.snapshots()
                .stream()
                .sorted(Comparator.comparing(
                        AssessmentInputResponse.SnapshotResponse
                                ::reportingPeriod
                ))
                .map(snapshot -> new FinancialSnapshotRequest(
                        snapshot.snapshotId(),
                        snapshot.msmeId(),
                        snapshot.reportingPeriod(),
                        snapshot.revenue(),
                        snapshot.totalReceivables(),
                        snapshot.overdueReceivables(),
                        snapshot.creditUsed(),
                        input.sanctionedCreditLimit()
                ))
                .toList();
    }

    private void validateInput(
            AssessmentInputResponse input,
            Long msmeId,
            LocalDate reportingPeriod) {

        if (input == null
                || !validator.validate(input).isEmpty()) {

            throw invalidResponse();
        }

        if (!msmeId.equals(input.msmeId())
                || !reportingPeriod.equals(input.reportingPeriod())) {

            throw invalidResponse();
        }

        Set<LocalDate> expectedPeriods = Set.of(
                reportingPeriod.minusMonths(2),
                reportingPeriod.minusMonths(1),
                reportingPeriod
        );

        Set<LocalDate> returnedPeriods = new HashSet<>();
        Set<Long> snapshotIds = new HashSet<>();

        for (var snapshot : input.snapshots()) {

            if (!msmeId.equals(snapshot.msmeId())
                    || !expectedPeriods.contains(
                            snapshot.reportingPeriod()
                    )
                    || !returnedPeriods.add(
                            snapshot.reportingPeriod()
                    )
                    || !snapshotIds.add(snapshot.snapshotId())
                    || snapshot.overdueReceivables().compareTo(
                            snapshot.totalReceivables()
                    ) > 0) {

                throw invalidResponse();
            }
        }

        if (!returnedPeriods.equals(expectedPeriods)) {
            throw invalidResponse();
        }
    }

    private MsmeServiceException invalidResponse() {
        return new MsmeServiceException(
                HttpStatus.BAD_GATEWAY,
                "MS1 returned invalid assessment input"
        );
    }
}