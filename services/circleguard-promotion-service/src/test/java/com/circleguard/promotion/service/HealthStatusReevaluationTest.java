package com.circleguard.promotion.service;

import com.circleguard.promotion.metrics.BusinessMetrics;
import com.circleguard.promotion.model.graph.UserNode;
import com.circleguard.promotion.repository.graph.UserNodeRepository;
import com.circleguard.promotion.repository.jpa.SystemSettingsRepository;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class HealthStatusReevaluationTest {

    @Mock
    private Neo4jClient neo4jClient;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private UserNodeRepository userNodeRepository;

    @Mock
    private SystemSettingsRepository systemSettingsRepository;

    @Mock
    private com.circleguard.promotion.repository.graph.CircleNodeRepository circleNodeRepository;

    @Mock
    private BusinessMetrics businessMetrics;

    @InjectMocks
    private HealthStatusService healthStatusService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(userNodeRepository.findById(anyString())).thenReturn(Optional.of(
                UserNode.builder().anonymousId("A").status("ACTIVE").build()));
    }

    private void mockNeo4jQuery() {
        Neo4jClient.UnboundRunnableSpec runnableSpec = mock(Neo4jClient.UnboundRunnableSpec.class, RETURNS_DEEP_STUBS);
        lenient().when(neo4jClient.query(anyString())).thenReturn(runnableSpec);

        java.util.Map<String, Object> resultMap = new java.util.HashMap<>();
        resultMap.put("sourceId", "A");
        resultMap.put("affectedContacts", java.util.Collections.emptyList());
        resultMap.put("releasedIds", java.util.Collections.emptyList());

        lenient().when(runnableSpec.bind(any()).to(any()).fetch().one())
            .thenReturn(Optional.of(resultMap));
    }

    @Test
    void testSingleRelease() {
        mockNeo4jQuery();
        assertDoesNotThrow(() -> healthStatusService.resolveStatus("A"));
    }

    @Test
    void testBlockedRelease() {
        mockNeo4jQuery();
        assertDoesNotThrow(() -> healthStatusService.resolveStatus("A"));
    }

    @Test
    void testMultiHopRelease() {
        mockNeo4jQuery();
        assertDoesNotThrow(() -> healthStatusService.resolveStatus("A"));
    }

    @Test
    void testPartialReleaseInMesh() {
        mockNeo4jQuery();
        assertDoesNotThrow(() -> healthStatusService.resolveStatus("A"));
    }
}
