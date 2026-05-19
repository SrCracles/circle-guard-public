package com.circleguard.gateway.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Key;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class QrValidationServiceAdditionalTest {

    private QrValidationService service;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private final String secret = "my-super-secret-test-key-32-chars-long";

    @BeforeEach
    void setUp() {
        redisTemplate = Mockito.mock(StringRedisTemplate.class);
        valueOps = Mockito.mock(ValueOperations.class);
        Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOps);

        service = new QrValidationService(redisTemplate);
        ReflectionTestUtils.setField(service, "qrSecret", secret);
    }

    @Test
    void shouldDenyAccessForPotentialUser() {
        String anonymousId = UUID.randomUUID().toString();
        Key key = Keys.hmacShaKeyFor(secret.getBytes());
        String token = Jwts.builder()
                .setSubject(anonymousId)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        Mockito.when(valueOps.get("user:status:" + anonymousId)).thenReturn("POTENTIAL");

        QrValidationService.ValidationResult result = service.validateToken(token);

        assertFalse(result.valid());
        assertEquals("RED", result.status());
        assertTrue(result.message().contains("Health Risk Detected"));
    }

    @Test
    void shouldDenyAccessForExpiredToken() {
        String anonymousId = UUID.randomUUID().toString();
        Key key = Keys.hmacShaKeyFor(secret.getBytes());
        String token = Jwts.builder()
                .setSubject(anonymousId)
                .setExpiration(new Date(System.currentTimeMillis() - 1000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        QrValidationService.ValidationResult result = service.validateToken(token);

        assertFalse(result.valid());
        assertEquals("RED", result.status());
        assertTrue(result.message().contains("Invalid or Expired Token"));
    }

    @Test
    void shouldDenyAccessForTamperedToken() {
        String tamperedToken = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0.invalid-signature-part";

        QrValidationService.ValidationResult result = service.validateToken(tamperedToken);

        assertFalse(result.valid());
        assertEquals("RED", result.status());
    }

    @Test
    void shouldAllowAccessWhenRedisStatusIsClear() {
        String anonymousId = UUID.randomUUID().toString();
        Key key = Keys.hmacShaKeyFor(secret.getBytes());
        String token = Jwts.builder()
                .setSubject(anonymousId)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        Mockito.when(valueOps.get("user:status:" + anonymousId)).thenReturn("CLEAR");

        QrValidationService.ValidationResult result = service.validateToken(token);

        assertTrue(result.valid());
        assertEquals("GREEN", result.status());
    }

    @Test
    void shouldAllowAccessWhenRedisStatusIsNull() {
        String anonymousId = UUID.randomUUID().toString();
        Key key = Keys.hmacShaKeyFor(secret.getBytes());
        String token = Jwts.builder()
                .setSubject(anonymousId)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        Mockito.when(valueOps.get("user:status:" + anonymousId)).thenReturn(null);

        QrValidationService.ValidationResult result = service.validateToken(token);

        assertTrue(result.valid());
        assertEquals("GREEN", result.status());
    }
}
