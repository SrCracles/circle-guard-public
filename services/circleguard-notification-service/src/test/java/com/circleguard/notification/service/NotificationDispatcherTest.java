package com.circleguard.notification.service;

import com.circleguard.notification.metrics.BusinessMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    @Mock
    private EmailService emailService;

    @Mock
    private SmsService smsService;

    @Mock
    private PushService pushService;

    @Mock
    private TemplateService templateService;

    @Mock
    private BusinessMetrics businessMetrics;

    @InjectMocks
    private NotificationDispatcher dispatcher;

    @Test
    void shouldDispatchToAllChannelsConcurrently() throws Exception {
        when(emailService.sendAsync(any(), any())).thenReturn(CompletableFuture.completedFuture(null));
        when(smsService.sendAsync(any(), any())).thenReturn(CompletableFuture.completedFuture(null));
        when(pushService.sendAsync(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));
        when(templateService.generateEmailContent(any(), any())).thenReturn("email");
        when(templateService.generatePushContent(any())).thenReturn("push");
        when(templateService.generatePushMetadata(any())).thenReturn(Map.of());
        when(templateService.generateSmsContent(any())).thenReturn("sms");

        dispatcher.dispatch("user-123", "Your health status has changed.");

        verify(emailService).sendAsync(eq("user-123"), any());
        verify(smsService).sendAsync(eq("user-123"), any());
        verify(pushService).sendAsync(eq("user-123"), any(), any());
    }
}
