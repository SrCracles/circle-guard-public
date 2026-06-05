package com.circleguard.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PushServiceImplTest {

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private WebClient webClient;

    private PushServiceImpl pushService;

    @BeforeEach
    void setUp() {
        when(webClientBuilder.baseUrl(any())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);
        pushService = new PushServiceImpl(webClientBuilder, "http://localhost:8080");
        ReflectionTestUtils.setField(pushService, "auditLogService", auditLogService);
        ReflectionTestUtils.setField(pushService, "gotifyToken", "MOCK_TOKEN");
    }

    @Test
    void shouldSendMockPushAndLogSuccess() {
        CompletableFuture<Void> future = pushService.sendAsync("user-123", "Alert message", Map.of("key", "value"));

        assertThat(future).isCompleted();
        verify(auditLogService).logDelivery(eq("user-123"), eq("PUSH"), eq("SUCCESS"), any());
    }

    @Test
    void shouldRecoverAfterMaxRetries() {
        Exception ex = new RuntimeException("Push failed");
        CompletableFuture<Void> future = pushService.recover(ex, "user-123", "Alert", Map.of());

        assertThat(future).isCompletedExceptionally();
        verify(auditLogService).logDelivery("user-123", "PUSH", "FAILED", null);
    }
}
