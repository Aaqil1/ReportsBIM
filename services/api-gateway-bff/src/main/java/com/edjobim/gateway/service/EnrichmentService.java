package com.edjobim.gateway.service;

import com.edjobim.gateway.dto.EnrichmentData;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class EnrichmentService {
    private static final Logger logger = LoggerFactory.getLogger(EnrichmentService.class);
    
    private final WebClient webClient;
    private final String performanceServiceUrl;
    private final String aimsConfigServiceUrl;

    public EnrichmentService(WebClient.Builder webClientBuilder,
                            @Value("${services.performance.url}") String performanceServiceUrl,
                            @Value("${services.aims-config.url}") String aimsConfigServiceUrl) {
        this.webClient = webClientBuilder.build();
        this.performanceServiceUrl = performanceServiceUrl;
        this.aimsConfigServiceUrl = aimsConfigServiceUrl;
    }

    @CircuitBreaker(name = "enrichment", fallbackMethod = "fallbackEnrichment")
    @Retry(name = "enrichment")
    @TimeLimiter(name = "enrichment")
    public CompletableFuture<EnrichmentData> enrich(String clientId, String startDate, String endDate) {
        String correlationId = MDC.get("correlationId");
        
        logger.info("Enriching request for clientId: {}", clientId);

        Mono<Map<String, Object>> performanceMono = webClient.get()
                .uri(performanceServiceUrl + "/api/v1/performance/client/{clientId}?startDate={startDate}&endDate={endDate}", 
                        clientId, startDate, endDate)
                .header("X-Correlation-ID", correlationId)
                .retrieve()
                .bodyToMono(Map.class)
                .onErrorReturn(new HashMap<>());

        Mono<Map<String, Object>> aimsConfigMono = webClient.get()
                .uri(aimsConfigServiceUrl + "/api/v1/config/aims/{clientId}", clientId)
                .header("X-Correlation-ID", correlationId)
                .retrieve()
                .bodyToMono(Map.class)
                .onErrorReturn(new HashMap<>());

        return Mono.zip(performanceMono, aimsConfigMono)
                .map(tuple -> {
                    EnrichmentData data = new EnrichmentData();
                    data.setPerformanceData(tuple.getT1());
                    data.setAimsConfig(tuple.getT2());
                    return data;
                })
                .toFuture();
    }

    public CompletableFuture<EnrichmentData> fallbackEnrichment(String clientId, String startDate, String endDate, Exception ex) {
        logger.warn("Enrichment fallback triggered for clientId: {}", clientId, ex);
        EnrichmentData data = new EnrichmentData();
        data.setPerformanceData(new HashMap<>());
        data.setAimsConfig(new HashMap<>());
        return CompletableFuture.completedFuture(data);
    }
}
