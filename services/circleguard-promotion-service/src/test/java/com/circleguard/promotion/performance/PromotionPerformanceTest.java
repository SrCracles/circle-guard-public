package com.circleguard.promotion.performance;

import com.circleguard.promotion.model.jpa.SystemSettings;
import com.circleguard.promotion.repository.jpa.SystemSettingsRepository;
import com.circleguard.promotion.service.HealthStatusService;
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

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PromotionPerformanceTest {

    @Mock
    private Neo4jClient neo4jClient;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SystemSettingsRepository systemSettingsRepository;

    @Mock
    private com.circleguard.promotion.repository.graph.CircleNodeRepository circleNodeRepository;

    @InjectMocks
    private HealthStatusService healthStatusService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        SystemSettings settings = SystemSettings.builder()
                .encounterWindowDays(14)
                .mandatoryFenceDays(14)
                .unconfirmedFencingEnabled(true)
                .autoThresholdSeconds(3600L)
                .build();
        when(systemSettingsRepository.getSettings()).thenReturn(Optional.of(settings));
    }

    @Test
    void benchmarkPromotionPerformance() {
        String anonymousId = "user-123";

        Neo4jClient.UnboundRunnableSpec runnableSpec = mock(Neo4jClient.UnboundRunnableSpec.class, RETURNS_DEEP_STUBS);
        when(neo4jClient.query(anyString())).thenReturn(runnableSpec);

        java.util.Map<String, Object> resultMap = new java.util.HashMap<>();
        resultMap.put("sourceId", anonymousId);
        resultMap.put("affectedContacts", java.util.Collections.emptyList());

        when(runnableSpec.bind(anyString()).to(anyString())
                .bind(anyString()).to(anyString())
                .bind(anyLong()).to(anyString())
                .fetch().one())
            .thenReturn(Optional.of(resultMap));

        healthStatusService.updateStatus(anonymousId, "CONFIRMED");

        verify(kafkaTemplate, atLeastOnce()).send(anyString(), eq(anonymousId), any());
    }
}
