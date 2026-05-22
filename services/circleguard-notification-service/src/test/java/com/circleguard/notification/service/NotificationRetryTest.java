package com.circleguard.notification.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class NotificationRetryTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private EmailServiceImpl emailService;

    @Test
    void testEmailRetryLogic() throws Exception {
        doThrow(new RuntimeException("Mail server down"))
            .when(mailSender).send(any(SimpleMailMessage.class));

        assertThrows(RuntimeException.class, () -> emailService.sendAsync("user-1", "test message"));

        verify(mailSender).send(any(SimpleMailMessage.class));
        verify(auditLogService).logDelivery(anyString(), eq("EMAIL"), eq("RETRY"), any());
    }
}
