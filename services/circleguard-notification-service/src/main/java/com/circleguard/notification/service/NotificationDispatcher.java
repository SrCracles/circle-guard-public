package com.circleguard.notification.service;

import com.circleguard.notification.metrics.BusinessMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatcher {

    private final EmailService emailService;
    private final SmsService smsService;
    private final PushService pushService;
    private final TemplateService templateService;
    private final BusinessMetrics businessMetrics;

    public void dispatch(String userId, String status) {
        log.info("Dispatching contextual multi-channel notifications for user: {} with status: {}", userId, status);
        businessMetrics.recordNotificationSent();
        
        String emailContent = templateService.generateEmailContent(status, userId);
        String pushContent = templateService.generatePushContent(status);
        Map<String, String> pushMetadata = templateService.generatePushMetadata(status);
        String smsContent = templateService.generateSmsContent(status);

        CompletableFuture.allOf(
            emailService.sendAsync(userId, emailContent),
            smsService.sendAsync(userId, smsContent),
            pushService.sendAsync(userId, pushContent, pushMetadata)
        ).handle((result, ex) -> {
            if (ex != null) {
                log.error("Error during multi-channel dispatch for user {}: {}", userId, ex.getMessage());
            } else {
                log.info("Multi-channel dispatch completed successfully for user: {}", userId);
            }
            return result;
        });
    }
}
