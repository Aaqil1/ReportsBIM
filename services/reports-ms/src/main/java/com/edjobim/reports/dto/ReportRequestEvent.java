package com.edjobim.reports.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class ReportRequestEvent {
    @JsonProperty("reportRequestId")
    private String reportRequestId;

    @JsonProperty("reportType")
    private String reportType;

    @JsonProperty("clientId")
    private String clientId;

    @JsonProperty("startDate")
    private String startDate;

    @JsonProperty("endDate")
    private String endDate;

    @JsonProperty("enrichmentData")
    private Map<String, Object> enrichmentData;

    public String getReportRequestId() {
        return reportRequestId;
    }

    public void setReportRequestId(String reportRequestId) {
        this.reportRequestId = reportRequestId;
    }

    public String getReportType() {
        return reportType;
    }

    public void setReportType(String reportType) {
        this.reportType = reportType;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public Map<String, Object> getEnrichmentData() {
        return enrichmentData;
    }

    public void setEnrichmentData(Map<String, Object> enrichmentData) {
        this.enrichmentData = enrichmentData;
    }
}
