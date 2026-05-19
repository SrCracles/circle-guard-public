package com.circleguard.promotion.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PromotionToNotificationKafkaTest {

    @InjectMocks
    private HealthStatusService healthStatusService;

    @Mock
    private Neo4jClient neo4jClient;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private com.circleguard.promotion.repository.jpa.SystemSettingsRepository systemSettingsRepository;

    @Mock
    private com.circleguard.promotion.repository.graph.CircleNodeRepository circleNodeRepository;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void setup() {
        when(systemSettingsRepository.getSettings()).thenReturn(Optional.of(
                com.circleguard.promotion.model.jpa.SystemSettings.builder()
                        .encounterWindowDays(14)
                        .mandatoryFenceDays(14)
                        .unconfirmedFencingEnabled(true)
                        .autoThresholdSeconds(3600L)
                        .build()
        ));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void updateStatus_ToConfirmed_EmitsStatusChangedAndPriorityAlert() {
        String anonymousId = "user-123";
        Neo4jClient.UnboundRunnableSpec runnableSpec = mock(Neo4jClient.UnboundRunnableSpec.class, RETURNS_DEEP_STUBS);

        when(neo4jClient.query(anyString())).thenReturn(runnableSpec);

        Map<String, Object> result = new HashMap<>();
        result.put("sourceId", anonymousId);
        result.put("affectedContacts", List.of(
                Map.of("id", "contact-1", "status", "SUSPECT"),
                Map.of("id", "contact-2", "status", "PROBABLE")
        ));

        when(runnableSpec.bind(anyString()).to(anyString())
                .bind(anyString()).to(anyString())
                .bind(anyLong()).to(anyString())
                .fetch().one())
                .thenReturn(Optional.of(result));

        when(circleNodeRepository.findNewlyFencedCircles(anyString())).thenReturn(Collections.emptyList());

        healthStatusService.updateStatus(anonymousId, "CONFIRMED");

        verify(kafkaTemplate).send(eq("promotion.status.changed"), eq(anonymousId), argThat(payload -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> p = (Map<String, Object>) payload;
            return "user-123".equals(p.get("anonymousId")) && "CONFIRMED".equals(p.get("status"));
        }));

        verify(kafkaTemplate).send(eq("alert.priority"), eq(anonymousId), argThat(payload -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> p = (Map<String, Object>) payload;
            return "user-123".equals(p.get("anonymousId")) &&
                    Integer.valueOf(2).equals(p.get("affectedCount")) &&
                    "CONFIRMED_CASE".equals(p.get("eventType"));
        }));
    }

    @Test
    void updateStatus_ToSuspect_EmitsStatusChangedOnly() {
        String anonymousId = "user-456";
        Neo4jClient.UnboundRunnableSpec runnableSpec = mock(Neo4jClient.UnboundRunnableSpec.class, RETURNS_DEEP_STUBS);

        when(neo4jClient.query(anyString())).thenReturn(runnableSpec);

        Map<String, Object> result = new HashMap<>();
        result.put("sourceId", anonymousId);
        result.put("affectedContacts", Collections.emptyList());

        when(runnableSpec.bind(anyString()).to(anyString())
                .bind(anyString()).to(anyString())
                .bind(anyLong()).to(anyString())
                .fetch().one())
                .thenReturn(Optional.of(result));

        when(circleNodeRepository.findNewlyFencedCircles(anyString())).thenReturn(Collections.emptyList());

        healthStatusService.updateStatus(anonymousId, "SUSPECT");

        verify(kafkaTemplate).send(eq("promotion.status.changed"), eq(anonymousId), argThat(payload -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> p = (Map<String, Object>) payload;
            return "user-456".equals(p.get("anonymousId")) && "SUSPECT".equals(p.get("status"));
        }));

        verify(kafkaTemplate, never()).send(eq("alert.priority"), anyString(), any());
    }
}
