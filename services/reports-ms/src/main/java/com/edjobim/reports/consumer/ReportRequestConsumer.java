package com.edjobim.reports.consumer;

import com.edjobim.reports.dto.ReportRequestEvent;
import com.edjobim.reports.service.ReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class ReportRequestConsumer {
    private static final Logger logger = LoggerFactory.getLogger(ReportRequestConsumer.class);
    private final ObjectMapper objectMapper;
    private final ReportService reportService;

    public ReportRequestConsumer(ObjectMapper objectMapper, ReportService reportService) {
        this.objectMapper = objectMapper;
        this.reportService = reportService;
    }

    @KafkaListener(topics = "report.requested", groupId = "reports-ms-group")
    public void consume(ConsumerRecord<String, String> record,
                       @Header(KafkaHeaders.RECEIVED_KEY) String key,
                       Acknowledgment acknowledgment) {
        String correlationId = extractCorrelationId(record);
        MDC.put("correlationId", correlationId);

        try {
            logger.info("Received report request: key={}, partition={}, offset={}", 
                    key, record.partition(), record.offset());

            ReportRequestEvent event = objectMapper.readValue(record.value(), ReportRequestEvent.class);
            reportService.processReportRequest(event, correlationId);

            // Manual acknowledgment after successful processing
            acknowledgment.acknowledge();
            logger.info("Successfully processed report request: {}", event.getReportRequestId());

        } catch (Exception e) {
            logger.error("Error processing report request", e);
            // In production, implement retry logic with exponential backoff
            // For now, acknowledge to prevent infinite retries (DLQ should handle)
            acknowledgment.acknowledge();
        } finally {
            MDC.clear();
        }
    }

    private String extractCorrelationId(ConsumerRecord<String, String> record) {
        if (record.headers() != null) {
            var header = record.headers().lastHeader("X-Correlation-ID");
            if (header != null) {
                return new String(header.value());
            }
        }
        return "unknown";
    }
}
