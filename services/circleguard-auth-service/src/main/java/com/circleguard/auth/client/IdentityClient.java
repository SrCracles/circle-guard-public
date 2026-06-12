package com.circleguard.auth.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.time.Duration;
import java.util.*;

@Component
public class IdentityClient {
    private final RestTemplate restTemplate;

    @Value("${identity.service.url:http://localhost:8083}")
    private String identityServiceUrl;

    public IdentityClient(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofMillis(3000))
                .readTimeout(Duration.ofMillis(3000))
                .build();
    }

    @CircuitBreaker(name = "identityService", fallbackMethod = "getAnonymousIdFallback")
    public UUID getAnonymousId(String realIdentity) {
        String url = identityServiceUrl + "/api/v1/identities/map";
        Map<String, String> request = Map.of("realIdentity", realIdentity);
        Map response = restTemplate.postForObject(url, request, Map.class);
        return UUID.fromString(response.get("anonymousId").toString());
    }

    public UUID getAnonymousIdFallback(String realIdentity, Throwable t) {
        System.err.println("Fallback activated for Identity Service. Returning default UUID.");
        return UUID.fromString("00000000-0000-0000-0000-000000000000");
    }
}
