package com.edjobim.reports.strategy;

import com.edjobim.reports.dto.ReportRequestEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class AssetAllocationReportStrategy implements ReportGenerationStrategy {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String generateReport(ReportRequestEvent request) {
        ObjectNode report = objectMapper.createObjectNode();
        report.put("reportType", "asset-allocation");
        report.put("reportRequestId", request.getReportRequestId());
        report.put("clientId", request.getClientId());
        
        ObjectNode allocation = objectMapper.createObjectNode();
        allocation.put("equity", 60.0);
        allocation.put("fixedIncome", 30.0);
        allocation.put("alternatives", 10.0);
        report.set("allocation", allocation);
        
        return report.toString();
    }

    @Override
    public String getReportType() {
        return "asset-allocation";
    }
}
