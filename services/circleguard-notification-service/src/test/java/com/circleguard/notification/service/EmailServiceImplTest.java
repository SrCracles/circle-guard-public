package com.circleguard.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceImplTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private EmailServiceImpl emailService;

    @BeforeEach
    void setUp() {
        // Set toggle to true by default for backward compatibility in tests
        ReflectionTestUtils.setField(emailService, "emailEnabled", true);
    }

    @Test
    void shouldSendEmailWhenToggleIsActive() throws Exception {
        String userId = "user-123";
        String message = "Health alert";

        CompletableFuture<Void> future = emailService.sendAsync(userId, message);
        future.join(); // wait for completion

        verify(mailSender).send(any(SimpleMailMessage.class));
        verify(auditLogService).logDelivery(eq(userId), eq("EMAIL"), eq("SUCCESS"), any());
    }

    @Test
    void shouldNotSendEmailWhenToggleIsInactive() throws Exception {
        ReflectionTestUtils.setField(emailService, "emailEnabled", false);

        String userId = "user-456";
        String message = "Health alert";

        CompletableFuture<Void> future = emailService.sendAsync(userId, message);
        future.join(); // wait for completion

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
        verify(auditLogService).logDelivery(eq(userId), eq("EMAIL"), eq("SKIPPED_TOGGLE"), any());
    }
}
