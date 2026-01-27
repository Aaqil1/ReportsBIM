package com.edjobim.aims.controller;

import com.edjobim.aims.dto.AimsConfig;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/config")
public class ConfigController {

    @GetMapping("/aims/{clientId}")
    public ResponseEntity<AimsConfig> getAimsConfig(
            @PathVariable String clientId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        
        MDC.put("correlationId", correlationId);
        
        // Mock AIMS config data
        AimsConfig config = new AimsConfig();
        config.setClientId(clientId);
        config.setBenchmarkId("SP500");
        config.setRiskProfile("MODERATE");
        config.setInvestmentHorizon("LONG_TERM");
        
        return ResponseEntity.ok(config);
    }
}
