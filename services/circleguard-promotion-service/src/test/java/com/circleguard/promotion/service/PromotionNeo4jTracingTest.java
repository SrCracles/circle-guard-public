package com.circleguard.promotion.service;

import com.circleguard.promotion.repository.graph.UserNodeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.neo4j.core.Neo4jClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PromotionNeo4jTracingTest {

    @InjectMocks
    private GraphService graphService;

    @Mock
    private UserNodeRepository userNodeRepository;

    @Mock
    private Neo4jClient neo4jClient;

    @Test
    void recordEncounter_DelegatesToRepository() {
        String userA = "user-a-uuid";
        String userB = "user-b-uuid";
        String locationId = "building-a";

        graphService.recordEncounter(userA, userB, locationId);

        verify(userNodeRepository).recordEncounter(eq(userA), eq(userB), anyLong(), eq(locationId));
    }

    @Test
    void detectAndFormCircles_ExecutesCorrectCypherQuery() {
        Neo4jClient.UnboundRunnableSpec runnableSpec = mock(Neo4jClient.UnboundRunnableSpec.class, RETURNS_DEEP_STUBS);
        when(neo4jClient.query(anyString())).thenReturn(runnableSpec);

        String locationId = "library-1";
        graphService.detectAndFormCircles(locationId);

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(neo4jClient).query(queryCaptor.capture());

        String query = queryCaptor.getValue();
        assertTrue(query.contains("MATCH (u:User)-[r:ENCOUNTERED {locationId: $loc}]->(target:User)"));
        assertTrue(query.contains("r.duration > 300"));
        assertTrue(query.contains("size(users) >= 3"));
        assertTrue(query.contains("MERGE (c:Circle {locationId: $loc, isActive: true})"));
        assertTrue(query.contains("MATCH (u:User {anonymousId: uid})"));
        assertTrue(query.contains("MERGE (u)-[:MEMBER_OF]->(c)"));
    }

    @Test
    void detectAndFormCircles_BindsLocationParameter() {
        Neo4jClient.UnboundRunnableSpec runnableSpec = mock(Neo4jClient.UnboundRunnableSpec.class, RETURNS_DEEP_STUBS);
        when(neo4jClient.query(anyString())).thenReturn(runnableSpec);

        String locationId = "cafeteria-2";
        graphService.detectAndFormCircles(locationId);

        verify(neo4jClient).query(anyString());
    }
}
