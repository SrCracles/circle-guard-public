package com.circleguard.notification.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class AuditLogServiceTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private AuditLogService auditLogService;

    @Test
    void shouldLogDeliveryAndSendKafkaEvent() {
        auditLogService.logDelivery("user-1", "EMAIL", "SUCCESS", "corr-123");

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(kafkaTemplate).send(eq("notification.audit"), eq("user-1"), captor.capture());

        Map<String, Object> event = captor.getValue();
        assertThat(event.get("userId")).isEqualTo("user-1");
        assertThat(event.get("channel")).isEqualTo("EMAIL");
        assertThat(event.get("status")).isEqualTo("SUCCESS");
        assertThat(event.get("correlationId")).isEqualTo("corr-123");
        assertThat(event).containsKeys("eventId", "timestamp");
    }

    @Test
    void shouldGenerateCorrelationIdWhenNull() {
        auditLogService.logDelivery("user-2", "SMS", "FAILED", null);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(kafkaTemplate).send(eq("notification.audit"), eq("user-2"), captor.capture());

        Map<String, Object> event = captor.getValue();
        assertThat(event.get("correlationId")).isNotNull();
    }
}
