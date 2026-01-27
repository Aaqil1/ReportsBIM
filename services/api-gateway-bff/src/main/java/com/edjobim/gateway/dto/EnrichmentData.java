package com.edjobim.gateway.dto;

import java.util.Map;

public class EnrichmentData {
    private Map<String, Object> performanceData;
    private Map<String, Object> aimsConfig;

    public Map<String, Object> getPerformanceData() {
        return performanceData;
    }

    public void setPerformanceData(Map<String, Object> performanceData) {
        this.performanceData = performanceData;
    }

    public Map<String, Object> getAimsConfig() {
        return aimsConfig;
    }

    public void setAimsConfig(Map<String, Object> aimsConfig) {
        this.aimsConfig = aimsConfig;
    }
}
