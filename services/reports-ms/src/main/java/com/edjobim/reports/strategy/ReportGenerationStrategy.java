package com.edjobim.reports.strategy;

import com.edjobim.reports.dto.ReportRequestEvent;

public interface ReportGenerationStrategy {
    String generateReport(ReportRequestEvent request);
    String getReportType();
}
