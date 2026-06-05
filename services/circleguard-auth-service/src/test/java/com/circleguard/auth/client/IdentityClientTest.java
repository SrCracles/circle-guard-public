package com.circleguard.auth.client;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.ResourceAccessException;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import org.springframework.test.util.ReflectionTestUtils;

import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = com.circleguard.auth.AuthServiceApplication.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.flyway.enabled=false",
    "qr.secret=my-qr-secret-key-for-dev-1234567890",
    "qr.expiration=300"
})
public class IdentityClientTest {

    @Autowired
    private IdentityClient identityClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @MockBean
    private RestTemplate restTemplate;

    @BeforeEach
    public void setup() {
        // Replace the internal restTemplate with the mock
        ReflectionTestUtils.setField(identityClient, "restTemplate", restTemplate);
        
        // Reset Circuit Breaker state before each test
        circuitBreakerRegistry.circuitBreaker("identityService").transitionToClosedState();
    }

    @Test
    public void testGetAnonymousId_Success() {
        UUID expectedId = UUID.randomUUID();
        when(restTemplate.postForObject(anyString(), any(Map.class), eq(Map.class)))
                .thenReturn(Map.of("anonymousId", expectedId.toString()));

        UUID actualId = identityClient.getAnonymousId("testUser");

        assertEquals(expectedId, actualId);
    }

    @Test
    public void testGetAnonymousId_FallbackActivatedOnTimeout() {
        // Simulate a timeout/ResourceAccessException
        when(restTemplate.postForObject(anyString(), any(Map.class), eq(Map.class)))
                .thenThrow(new ResourceAccessException("I/O error on POST request"));

        // When
        UUID actualId = identityClient.getAnonymousId("testUser");

        // Then it should return the fallback UUID
        UUID expectedFallbackId = UUID.fromString("00000000-0000-0000-0000-000000000000");
        assertEquals(expectedFallbackId, actualId);
    }
}
