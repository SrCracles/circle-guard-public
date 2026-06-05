package com.circleguard.auth.controller;

import com.circleguard.auth.service.QrTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class QrTokenControllerTest {

    @Mock
    private QrTokenService qrTokenService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private QrTokenController controller;

    @Test
    void shouldGenerateQrTokenSuccessfully() {
        UUID anonymousId = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
        String token = "mock-qr-token";

        when(authentication.getName()).thenReturn(anonymousId.toString());
        when(qrTokenService.generateQrToken(anonymousId)).thenReturn(token);

        ResponseEntity<Map<String, String>> response = controller.generateToken(authentication);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsEntry("qrToken", token);
        assertThat(response.getBody()).containsEntry("expiresIn", "60");
    }
}
