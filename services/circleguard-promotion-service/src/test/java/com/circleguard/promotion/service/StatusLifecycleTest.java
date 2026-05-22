package com.circleguard.promotion.service;

import com.circleguard.promotion.model.jpa.SystemSettings;
import com.circleguard.promotion.repository.jpa.SystemSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.Mockito;

@ExtendWith(MockitoExtension.class)
class StatusLifecycleTest {

    @InjectMocks
    private StatusLifecycleService lifecycleService;

    @Mock
    private Neo4jClient neo4jClient;

    @Mock
    private SystemSettingsRepository settingsRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void setup() {
        SystemSettings settings = SystemSettings.builder()
                .unconfirmedFencingEnabled(true)
                .autoThresholdSeconds(3600L)
                .mandatoryFenceDays(14)
                .encounterWindowDays(14)
                .build();
        when(settingsRepository.getSettings()).thenReturn(Optional.of(settings));
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void automaticTransition_ReleasesExpiredUsers() {
        Neo4jClient.UnboundRunnableSpec runnableSpec = mock(Neo4jClient.UnboundRunnableSpec.class, Mockito.RETURNS_DEEP_STUBS);
        when(neo4jClient.query(anyString())).thenReturn(runnableSpec);
        
        Map<String, Object> resultMap = Map.of(
            "releasedIds", List.of("EXPIRED_USER")
        );
        
        when(runnableSpec.bind(anyLong()).to(anyString())
                .fetch().one())
            .thenReturn(Optional.of(resultMap));

        lifecycleService.processAutomaticTransitions();

        verify(valueOperations).multiSet(ArgumentMatchers.anyMap());
        verify(kafkaTemplate).send(ArgumentMatchers.eq("promotion.status.changed"), ArgumentMatchers.eq("EXPIRED_USER"), ArgumentMatchers.anyMap());
    }

    @Test
    void automaticTransition_HandlesEmptyResults() {
        Neo4jClient.UnboundRunnableSpec runnableSpec = mock(Neo4jClient.UnboundRunnableSpec.class, Mockito.RETURNS_DEEP_STUBS);
        when(neo4jClient.query(anyString())).thenReturn(runnableSpec);
        
        Map<String, Object> resultMap = Map.of(
            "releasedIds", Collections.emptyList()
        );
        
        when(runnableSpec.bind(anyLong()).to(anyString())
                .fetch().one())
            .thenReturn(Optional.of(resultMap));

        lifecycleService.processAutomaticTransitions();

        verify(valueOperations, Mockito.never()).multiSet(ArgumentMatchers.anyMap());
    }
}
