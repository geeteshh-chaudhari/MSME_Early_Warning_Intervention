package com.msme.dto.snapshot;

import java.util.List;

public class SnapshotReadinessResponse {

    private String reportingPeriod;

    private boolean currentPeriodAvailable;

    private boolean previousPeriodAvailable;

    private boolean twoPeriodsPriorAvailable;

    private boolean assessmentReady;

    private List<String> missingPeriods;

    public SnapshotReadinessResponse() {
    }

    public SnapshotReadinessResponse(
            String reportingPeriod,
            boolean currentPeriodAvailable,
            boolean previousPeriodAvailable,
            boolean twoPeriodsPriorAvailable,
            boolean assessmentReady,
            List<String> missingPeriods) {

        this.reportingPeriod = reportingPeriod;
        this.currentPeriodAvailable = currentPeriodAvailable;
        this.previousPeriodAvailable = previousPeriodAvailable;
        this.twoPeriodsPriorAvailable = twoPeriodsPriorAvailable;
        this.assessmentReady = assessmentReady;
        this.missingPeriods = missingPeriods;
    }

    public String getReportingPeriod() {
        return reportingPeriod;
    }

    public void setReportingPeriod(
            String reportingPeriod) {

        this.reportingPeriod = reportingPeriod;
    }

    public boolean isCurrentPeriodAvailable() {
        return currentPeriodAvailable;
    }

    public void setCurrentPeriodAvailable(
            boolean currentPeriodAvailable) {

        this.currentPeriodAvailable =
                currentPeriodAvailable;
    }

    public boolean isPreviousPeriodAvailable() {
        return previousPeriodAvailable;
    }

    public void setPreviousPeriodAvailable(
            boolean previousPeriodAvailable) {

        this.previousPeriodAvailable =
                previousPeriodAvailable;
    }

    public boolean isTwoPeriodsPriorAvailable() {
        return twoPeriodsPriorAvailable;
    }

    public void setTwoPeriodsPriorAvailable(
            boolean twoPeriodsPriorAvailable) {

        this.twoPeriodsPriorAvailable =
                twoPeriodsPriorAvailable;
    }

    public boolean isAssessmentReady() {
        return assessmentReady;
    }

    public void setAssessmentReady(
            boolean assessmentReady) {

        this.assessmentReady = assessmentReady;
    }

    public List<String> getMissingPeriods() {
        return missingPeriods;
    }

    public void setMissingPeriods(
            List<String> missingPeriods) {

        this.missingPeriods = missingPeriods;
    }
}