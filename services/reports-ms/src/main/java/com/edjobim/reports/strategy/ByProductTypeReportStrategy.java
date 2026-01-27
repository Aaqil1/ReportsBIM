package com.edjobim.reports.strategy;

import com.edjobim.reports.dto.ReportRequestEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class ByProductTypeReportStrategy implements ReportGenerationStrategy {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String generateReport(ReportRequestEvent request) {
        ObjectNode report = objectMapper.createObjectNode();
        report.put("reportType", "by-product-type");
        report.put("reportRequestId", request.getReportRequestId());
        report.put("clientId", request.getClientId());
        
        ArrayNode products = objectMapper.createArrayNode();
        String[] productTypes = {"EQUITY", "FIXED_INCOME", "ALTERNATIVES"};
        for (String type : productTypes) {
            ObjectNode product = objectMapper.createObjectNode();
            product.put("productType", type);
            product.put("allocation", 33.33);
            product.put("value", 1000000.0);
            products.add(product);
        }
        report.set("products", products);
        
        return report.toString();
    }

    @Override
    public String getReportType() {
        return "by-product-type";
    }
}
