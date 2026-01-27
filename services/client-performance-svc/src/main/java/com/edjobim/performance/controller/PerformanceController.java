package com.edjobim.performance.controller;

import com.edjobim.performance.dto.PerformanceData;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/performance")
public class PerformanceController {

    @GetMapping("/client/{clientId}")
    public ResponseEntity<PerformanceData> getClientPerformance(
            @PathVariable String clientId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        
        MDC.put("correlationId", correlationId);
        
        // Mock performance data
        PerformanceData data = new PerformanceData();
        data.setClientId(clientId);
        data.setTotalReturn(12.5);
        data.setVolatility(8.3);
        data.setSharpeRatio(1.51);
        data.setStartDate(startDate);
        data.setEndDate(endDate);
        
        return ResponseEntity.ok(data);
    }
}
