package com.edjobim.reports.strategy;

import com.edjobim.reports.dto.ReportRequestEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class PerformanceReportStrategy implements ReportGenerationStrategy {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String generateReport(ReportRequestEvent request) {
        ObjectNode report = objectMapper.createObjectNode();
        report.put("reportType", "performance");
        report.put("reportRequestId", request.getReportRequestId());
        report.put("clientId", request.getClientId());
        report.put("startDate", request.getStartDate());
        report.put("endDate", request.getEndDate());
        
        // Mock performance metrics
        ObjectNode metrics = objectMapper.createObjectNode();
        metrics.put("totalReturn", 12.5);
        metrics.put("volatility", 8.3);
        metrics.put("sharpeRatio", 1.51);
        metrics.put("maxDrawdown", -5.2);
        report.set("metrics", metrics);
        
        if (request.getEnrichmentData() != null) {
            report.set("enrichmentData", objectMapper.valueToTree(request.getEnrichmentData()));
        }
        
        return report.toString();
    }

    @Override
    public String getReportType() {
        return "performance";
    }
}
