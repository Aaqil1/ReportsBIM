package com.edjobim.gateway.service;

import com.edjobim.gateway.dto.EnrichmentData;
import com.edjobim.gateway.dto.ReportRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class ReportService {
    private static final Logger logger = LoggerFactory.getLogger(ReportService.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final EnrichmentService enrichmentService;

    public ReportService(KafkaTemplate<String, Object> kafkaTemplate, EnrichmentService enrichmentService) {
        this.kafkaTemplate = kafkaTemplate;
        this.enrichmentService = enrichmentService;
    }

    public String requestReport(ReportRequest request) {
        String correlationId = MDC.get("correlationId");
        String reportRequestId = UUID.randomUUID().toString();

        logger.info("Requesting report: type={}, clientId={}, requestId={}", 
                request.getReportType(), request.getClientId(), reportRequestId);

        // BFF aggregation: enrich request by calling downstream services
        CompletableFuture<EnrichmentData> enrichmentFuture = enrichmentService.enrich(
                request.getClientId(), 
                request.getStartDate(), 
                request.getEndDate()
        );

        // Wait for enrichment (with timeout handled by Resilience4j)
        EnrichmentData enrichmentData;
        try {
            enrichmentData = enrichmentFuture.get();
        } catch (Exception e) {
            logger.warn("Enrichment failed, proceeding with empty data", e);
            enrichmentData = new EnrichmentData();
        }

        // Build Kafka event
        Map<String, Object> event = new HashMap<>();
        event.put("reportRequestId", reportRequestId);
        event.put("reportType", request.getReportType());
        event.put("clientId", request.getClientId());
        event.put("startDate", request.getStartDate());
        event.put("endDate", request.getEndDate());
        
        Map<String, Object> enrichmentMap = new HashMap<>();
        enrichmentMap.put("performanceData", enrichmentData.getPerformanceData());
        enrichmentMap.put("aimsConfig", enrichmentData.getAimsConfig());
        event.put("enrichmentData", enrichmentMap);

        // Publish to Kafka with correlation ID in headers
        kafkaTemplate.send(MessageBuilder
                .withPayload(event)
                .setHeader(KafkaHeaders.TOPIC, "report.requested")
                .setHeader(KafkaHeaders.KEY, reportRequestId)
                .setHeader("X-Correlation-ID", correlationId)
                .build());

        logger.info("Published report request event: {}", reportRequestId);
        return reportRequestId;
    }
}
