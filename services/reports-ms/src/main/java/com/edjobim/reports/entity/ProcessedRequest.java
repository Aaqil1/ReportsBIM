package com.edjobim.reports.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "processed_requests", indexes = @Index(name = "idx_request_id", columnList = "report_request_id"))
public class ProcessedRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_request_id", unique = true, nullable = false)
    private String reportRequestId;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    public ProcessedRequest() {}

    public ProcessedRequest(String reportRequestId) {
        this.reportRequestId = reportRequestId;
        this.processedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getReportRequestId() {
        return reportRequestId;
    }

    public void setReportRequestId(String reportRequestId) {
        this.reportRequestId = reportRequestId;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }
}
