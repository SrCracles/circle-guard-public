package com.circleguard.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenServiceTest {

    private JwtTokenService jwtTokenService;
    private final String secret = "my-super-secret-test-key-32-chars-long";
    private final long expiration = 3600000; // 1 hour

    @BeforeEach
    void setUp() {
        jwtTokenService = new JwtTokenService(secret, expiration);
    }

    @Test
    void shouldGenerateTokenWithCorrectSubject() {
        UUID anonymousId = UUID.randomUUID();
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "user", null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );

        String token = jwtTokenService.generateToken(anonymousId, auth);

        assertNotNull(token);
        assertEquals(3, token.split("\\.").length); // header.payload.signature

        Claims claims = Jwts.parserBuilder()
                .setSigningKey(secret.getBytes())
                .build()
                .parseClaimsJws(token)
                .getBody();

        assertEquals(anonymousId.toString(), claims.getSubject());
    }

    @Test
    void shouldIncludePermissionsInClaims() {
        UUID anonymousId = UUID.randomUUID();
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "user", null, java.util.List.of(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("alert:receive_priority")
                )
        );

        String token = jwtTokenService.generateToken(anonymousId, auth);

        Claims claims = Jwts.parserBuilder()
                .setSigningKey(secret.getBytes())
                .build()
                .parseClaimsJws(token)
                .getBody();

        @SuppressWarnings("unchecked")
        java.util.List<String> permissions = claims.get("permissions", java.util.List.class);
        assertNotNull(permissions);
        assertTrue(permissions.contains("ROLE_USER"));
        assertTrue(permissions.contains("alert:receive_priority"));
    }

    @Test
    void shouldSetExpirationDate() {
        UUID anonymousId = UUID.randomUUID();
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "user", null, Collections.emptyList()
        );

        long before = System.currentTimeMillis();
        String token = jwtTokenService.generateToken(anonymousId, auth);
        long after = System.currentTimeMillis();

        Claims claims = Jwts.parserBuilder()
                .setSigningKey(secret.getBytes())
                .build()
                .parseClaimsJws(token)
                .getBody();

        assertNotNull(claims.getExpiration());
        long expectedMin = before + expiration - 5000; // 5s tolerance
        long expectedMax = after + expiration + 5000;
        assertTrue(claims.getExpiration().getTime() >= expectedMin && claims.getExpiration().getTime() <= expectedMax,
                "Expiration should be approximately 'now + expiration'");
    }
}
