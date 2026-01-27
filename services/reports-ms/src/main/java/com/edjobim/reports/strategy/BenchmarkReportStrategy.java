package com.edjobim.reports.strategy;

import com.edjobim.reports.dto.ReportRequestEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class BenchmarkReportStrategy implements ReportGenerationStrategy {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String generateReport(ReportRequestEvent request) {
        ObjectNode report = objectMapper.createObjectNode();
        report.put("reportType", "benchmark");
        report.put("reportRequestId", request.getReportRequestId());
        report.put("clientId", request.getClientId());
        
        ObjectNode comparison = objectMapper.createObjectNode();
        comparison.put("clientReturn", 12.5);
        comparison.put("benchmarkReturn", 10.2);
        comparison.put("outperformance", 2.3);
        report.set("comparison", comparison);
        
        return report.toString();
    }

    @Override
    public String getReportType() {
        return "benchmark";
    }
}
