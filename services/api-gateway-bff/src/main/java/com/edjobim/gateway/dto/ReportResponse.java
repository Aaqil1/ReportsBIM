package com.edjobim.gateway.dto;

public class ReportResponse {
    private String reportRequestId;
    private String status;
    private String message;

    public ReportResponse(String reportRequestId, String status, String message) {
        this.reportRequestId = reportRequestId;
        this.status = status;
        this.message = message;
    }

    public String getReportRequestId() {
        return reportRequestId;
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
