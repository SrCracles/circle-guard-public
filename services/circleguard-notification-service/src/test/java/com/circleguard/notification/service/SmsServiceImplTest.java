package com.circleguard.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SmsServiceImplTest {

    @Mock
    private AuditLogService auditLogService;

    private SmsServiceImpl smsService;

    @BeforeEach
    void setUp() {
        smsService = new SmsServiceImpl();
        ReflectionTestUtils.setField(smsService, "auditLogService", auditLogService);
        ReflectionTestUtils.setField(smsService, "accountSid", "AC_MOCK_SID");
        ReflectionTestUtils.setField(smsService, "authToken", "MOCK_TOKEN");
        ReflectionTestUtils.setField(smsService, "fromNumber", "+15550000000");
    }

    @Test
    void shouldSendMockSmsAndLogSuccess() {
        CompletableFuture<Void> future = smsService.sendAsync("user-123", "Test SMS");

        assertThat(future).isCompleted();

        ArgumentCaptor<String> corrCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).logDelivery(eq("user-123"), eq("SMS"), eq("SUCCESS"), corrCaptor.capture());
        assertThat(corrCaptor.getValue()).isNotNull();
    }

    @Test
    void shouldRecoverAfterMaxRetries() {
        Exception ex = new RuntimeException("SMS failed");
        CompletableFuture<Void> future = smsService.recover(ex, "user-123", "Test SMS");

        assertThat(future).isCompletedExceptionally();
        verify(auditLogService).logDelivery("user-123", "SMS", "FAILED", null);
    }
}
