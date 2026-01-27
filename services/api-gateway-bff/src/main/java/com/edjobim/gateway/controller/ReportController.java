package com.edjobim.gateway.controller;

import com.edjobim.gateway.dto.ReportRequest;
import com.edjobim.gateway.dto.ReportResponse;
import com.edjobim.gateway.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping("/request")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN')")
    public ResponseEntity<ReportResponse> requestReport(@Valid @RequestBody ReportRequest request) {
        String reportRequestId = reportService.requestReport(request);
        return ResponseEntity.ok(new ReportResponse(
                reportRequestId,
                "PENDING",
                "Report request submitted successfully"
        ));
    }

    @GetMapping("/{reportRequestId}/status")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN')")
    public ResponseEntity<ReportResponse> getReportStatus(@PathVariable String reportRequestId) {
        // In a real implementation, this would query the ArchiveDB or cache
        // For now, return a placeholder
        return ResponseEntity.ok(new ReportResponse(
                reportRequestId,
                "PENDING",
                "Status check not yet implemented - check reports-ms logs"
        ));
    }

    @GetMapping("/{reportRequestId}")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN')")
    public ResponseEntity<ReportResponse> getReport(@PathVariable String reportRequestId) {
        // In a real implementation, this would query ArchiveDB
        return ResponseEntity.ok(new ReportResponse(
                reportRequestId,
                "COMPLETED",
                "Report retrieval not yet implemented - query ArchiveDB directly"
        ));
    }
}
