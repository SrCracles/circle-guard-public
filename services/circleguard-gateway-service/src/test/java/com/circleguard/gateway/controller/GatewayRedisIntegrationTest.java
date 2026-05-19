package com.circleguard.gateway.controller;

import com.circleguard.gateway.service.QrValidationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class GatewayRedisIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private QrValidationService validationService;

    @Test
    void validToken_GreenStatus_AllowsAccess() throws Exception {
        String token = "valid-token-123";
        QrValidationService.ValidationResult greenResult =
                new QrValidationService.ValidationResult(true, "GREEN", "Welcome to Campus");

        when(validationService.validateToken(token)).thenReturn(greenResult);

        mockMvc.perform(post("/api/v1/gate/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.status").value("GREEN"))
                .andExpect(jsonPath("$.message").value("Welcome to Campus"));
    }

    @Test
    void contagiedToken_RedStatus_DeniesAccess() throws Exception {
        String token = "risk-token-456";
        QrValidationService.ValidationResult redResult =
                new QrValidationService.ValidationResult(false, "RED", "Access Denied: Health Risk Detected");

        when(validationService.validateToken(token)).thenReturn(redResult);

        mockMvc.perform(post("/api/v1/gate/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.status").value("RED"))
                .andExpect(jsonPath("$.message").value("Access Denied: Health Risk Detected"));
    }

    @Test
    void invalidToken_RedStatus_DeniesAccess() throws Exception {
        String token = "invalid-token";
        QrValidationService.ValidationResult invalidResult =
                new QrValidationService.ValidationResult(false, "RED", "Invalid or Expired Token");

        when(validationService.validateToken(token)).thenReturn(invalidResult);

        mockMvc.perform(post("/api/v1/gate/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.status").value("RED"));
    }
}
