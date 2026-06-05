package com.circleguard.auth.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.*;

@Component
public class IdentityClient {
    private final RestTemplate restTemplate;

    @Value("${identity.service.url:http://localhost:8083}")
    private String identityServiceUrl;

    public IdentityClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(3000);
        this.restTemplate = new RestTemplate(factory);
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
