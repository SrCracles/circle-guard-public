package com.circleguard.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class QrTokenServiceTest {

    private QrTokenService qrTokenService;

    @BeforeEach
    void setUp() {
        qrTokenService = new QrTokenService("my-secret-key-for-qr-token-generation", 60000L);
    }

    @Test
    void shouldGenerateQrTokenWithValidStructure() {
        UUID anonymousId = UUID.randomUUID();
        String token = qrTokenService.generateQrToken(anonymousId);

        assertThat(token).isNotNull();
        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void shouldGenerateDifferentTokensForDifferentAnonymousIds() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        String token1 = qrTokenService.generateQrToken(id1);
        String token2 = qrTokenService.generateQrToken(id2);

        assertThat(token1).isNotEqualTo(token2);
    }

    @Test
    void shouldIncludeAnonymousIdInTokenSubject() {
        UUID anonymousId = UUID.randomUUID();

        String token = qrTokenService.generateQrToken(anonymousId);

        io.jsonwebtoken.Claims claims = io.jsonwebtoken.Jwts.parserBuilder()
                .setSigningKey("my-secret-key-for-qr-token-generation".getBytes())
                .build()
                .parseClaimsJws(token)
                .getBody();

        assertThat(claims.getSubject()).isEqualTo(anonymousId.toString());
    }
}
