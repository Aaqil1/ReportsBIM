package com.edjobim.reports.strategy;

import com.edjobim.reports.dto.ReportRequestEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class DiversificationReportStrategy implements ReportGenerationStrategy {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String generateReport(ReportRequestEvent request) {
        ObjectNode report = objectMapper.createObjectNode();
        report.put("reportType", "diversification");
        report.put("reportRequestId", request.getReportRequestId());
        report.put("clientId", request.getClientId());
        
        ObjectNode metrics = objectMapper.createObjectNode();
        metrics.put("diversificationScore", 0.75);
        metrics.put("concentrationRisk", "LOW");
        metrics.put("sectorDiversity", 8);
        metrics.put("geographicDiversity", 5);
        report.set("metrics", metrics);
        
        return report.toString();
    }

    @Override
    public String getReportType() {
        return "diversification";
    }
}
