package com.circleguard.promotion.service;

import com.circleguard.promotion.exception.FenceException;
import com.circleguard.promotion.model.graph.UserNode;
import com.circleguard.promotion.model.jpa.SystemSettings;
import com.circleguard.promotion.repository.graph.UserNodeRepository;
import com.circleguard.promotion.repository.jpa.SystemSettingsRepository;
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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HealthStatusServiceTest {

    @InjectMocks
    private HealthStatusService healthStatusService;

    @Mock
    private UserNodeRepository userNodeRepository;

    @Mock
    private Neo4jClient neo4jClient;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private SystemSettingsRepository systemSettingsRepository;

    @Mock
    private com.circleguard.promotion.repository.graph.CircleNodeRepository circleNodeRepository;

    @Test
    void shouldUpdateStatusSuccessfully() {
        String anonymousId = "user-abc-123";
        String status = "GREEN";

        Neo4jClient.UnboundRunnableSpec runnableSpec = org.mockito.Mockito.mock(Neo4jClient.UnboundRunnableSpec.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        when(neo4jClient.query(anyString())).thenReturn(runnableSpec);
        
        java.util.Map<String, Object> resultMap = new java.util.HashMap<>();
        resultMap.put("sourceId", anonymousId);
        resultMap.put("affectedContacts", java.util.Collections.emptyList());
        
        when(runnableSpec.bind(anyString()).to(anyString())
                .bind(anyString()).to(anyString())
                .bind(ArgumentMatchers.anyLong()).to(anyString())
                .fetch().one())
            .thenReturn(Optional.of(resultMap));

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        assertDoesNotThrow(() -> healthStatusService.updateStatus(anonymousId, status));

        org.mockito.Mockito.verify(kafkaTemplate).send(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.any());
    }

    @Test
    void shouldThrowExceptionWhenUpdatingStatusToActiveWithinFenceWindow() {
        String anonymousId = "user-fenced";
        
        long fiveDaysAgo = System.currentTimeMillis() - (5L * 24 * 60 * 60 * 1000);
        UserNode user = UserNode.builder()
                .anonymousId(anonymousId)
                .status("SUSPECT")
                .statusUpdatedAt(fiveDaysAgo)
                .build();
        
        when(userNodeRepository.findById(anonymousId)).thenReturn(Optional.of(user));
        
        SystemSettings settings = SystemSettings.builder()
                .mandatoryFenceDays(14)
                .build();
        when(systemSettingsRepository.getSettings()).thenReturn(Optional.of(settings));

        assertThrows(FenceException.class, () -> healthStatusService.resolveStatus(anonymousId));
    }

    @Test
    void shouldAllowOverrideWhenWithinFenceWindow() {
        String anonymousId = "user-fenced-override";
        
        Neo4jClient.UnboundRunnableSpec runnableSpec = org.mockito.Mockito.mock(Neo4jClient.UnboundRunnableSpec.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        when(neo4jClient.query(anyString())).thenReturn(runnableSpec);
        
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        assertDoesNotThrow(() -> healthStatusService.resolveStatus(anonymousId, true));
    }
}
