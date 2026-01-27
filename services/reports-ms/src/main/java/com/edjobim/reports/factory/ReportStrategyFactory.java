package com.edjobim.reports.factory;

import com.edjobim.reports.strategy.*;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class ReportStrategyFactory {
    private final Map<String, ReportGenerationStrategy> strategies;

    public ReportStrategyFactory() {
        this.strategies = new HashMap<>();
        strategies.put("performance", new PerformanceReportStrategy());
        strategies.put("benchmark", new BenchmarkReportStrategy());
        strategies.put("by-product-type", new ByProductTypeReportStrategy());
        strategies.put("diversification", new DiversificationReportStrategy());
        strategies.put("asset-allocation", new AssetAllocationReportStrategy());
    }

    public ReportGenerationStrategy getStrategy(String reportType) {
        ReportGenerationStrategy strategy = strategies.get(reportType);
        if (strategy == null) {
            throw new IllegalArgumentException("Unknown report type: " + reportType);
        }
        return strategy;
    }
}
