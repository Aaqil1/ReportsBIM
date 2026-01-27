package com.edjobim.reports.service;

import com.edjobim.reports.dto.ReportRequestEvent;
import com.edjobim.reports.entity.ProcessedRequest;
import com.edjobim.reports.entity.Report;
import com.edjobim.reports.factory.ReportStrategyFactory;
import com.edjobim.reports.repository.ProcessedRequestRepository;
import com.edjobim.reports.repository.ReportRepository;
import com.edjobim.reports.strategy.ReportGenerationStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReportService {
    private static final Logger logger = LoggerFactory.getLogger(ReportService.class);

    private final ReportRepository reportRepository;
    private final ProcessedRequestRepository processedRequestRepository;
    private final ReportStrategyFactory strategyFactory;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public ReportService(ReportRepository reportRepository,
                        ProcessedRequestRepository processedRequestRepository,
                        ReportStrategyFactory strategyFactory,
                        KafkaTemplate<String, Object> kafkaTemplate) {
        this.reportRepository = reportRepository;
        this.processedRequestRepository = processedRequestRepository;
        this.strategyFactory = strategyFactory;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Transactional
    public void processReportRequest(ReportRequestEvent event, String correlationId) {
        MDC.put("correlationId", correlationId);
        String reportRequestId = event.getReportRequestId();

        logger.info("Processing report request: {}", reportRequestId);

        // Idempotent consumer: check if already processed
        if (processedRequestRepository.findByReportRequestId(reportRequestId).isPresent()) {
            logger.info("Report request {} already processed, skipping", reportRequestId);
            return;
        }

        try {
            // Create report entity
            Report report = new Report();
            report.setReportRequestId(reportRequestId);
            report.setReportType(event.getReportType());
            report.setStatus(Report.ReportStatus.PENDING);
            report.setCreatedAt(LocalDateTime.now());
            reportRepository.save(report);

            // Generate report using Strategy pattern
            ReportGenerationStrategy strategy = strategyFactory.getStrategy(event.getReportType());
            String reportData = strategy.generateReport(event);

            // Update report as completed
            report.setStatus(Report.ReportStatus.COMPLETED);
            report.setReportData(reportData);
            report.setCompletedAt(LocalDateTime.now());
            reportRepository.save(report);

            // Mark as processed (idempotency)
            processedRequestRepository.save(new ProcessedRequest(reportRequestId));

            // Emit completion event
            Map<String, Object> completedEvent = new HashMap<>();
            completedEvent.put("reportRequestId", reportRequestId);
            completedEvent.put("status", "COMPLETED");
            completedEvent.put("completedAt", LocalDateTime.now().toString());

            kafkaTemplate.send("report.completed", reportRequestId, completedEvent);

            logger.info("Report {} generated successfully", reportRequestId);

        } catch (Exception e) {
            logger.error("Failed to generate report {}", reportRequestId, e);
            
            // Update report as failed
            Report report = reportRepository.findByReportRequestId(reportRequestId)
                    .orElse(new Report());
            report.setReportRequestId(reportRequestId);
            report.setReportType(event.getReportType());
            report.setStatus(Report.ReportStatus.FAILED);
            report.setErrorMessage(e.getMessage());
            report.setCreatedAt(LocalDateTime.now());
            reportRepository.save(report);

            // Emit failure event
            Map<String, Object> failedEvent = new HashMap<>();
            failedEvent.put("reportRequestId", reportRequestId);
            failedEvent.put("status", "FAILED");
            failedEvent.put("error", e.getMessage());
            failedEvent.put("failedAt", LocalDateTime.now().toString());

            kafkaTemplate.send("report.failed", reportRequestId, failedEvent);
        }
    }
}
