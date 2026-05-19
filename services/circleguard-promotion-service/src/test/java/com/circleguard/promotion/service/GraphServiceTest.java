package com.circleguard.promotion.service;

import com.circleguard.promotion.repository.graph.UserNodeRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.neo4j.core.Neo4jClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GraphServiceTest {

    @Test
    void shouldRecordEncounterViaRepository() {
        UserNodeRepository userNodeRepository = mock(UserNodeRepository.class);
        Neo4jClient neo4jClient = mock(Neo4jClient.class);
        GraphService graphService = new GraphService(userNodeRepository, neo4jClient);

        String userA = "user-a-uuid";
        String userB = "user-b-uuid";
        String locationId = "building-a";

        graphService.recordEncounter(userA, userB, locationId);

        verify(userNodeRepository).recordEncounter(eq(userA), eq(userB), anyLong(), eq(locationId));
    }

    @Test
    void shouldExecuteCircleDetectionQuery() {
        UserNodeRepository userNodeRepository = mock(UserNodeRepository.class);
        Neo4jClient neo4jClient = mock(Neo4jClient.class);
        Neo4jClient.UnboundRunnableSpec runnableSpec = mock(Neo4jClient.UnboundRunnableSpec.class, RETURNS_DEEP_STUBS);

        when(neo4jClient.query(anyString())).thenReturn(runnableSpec);

        GraphService graphService = new GraphService(userNodeRepository, neo4jClient);
        String locationId = "library-1";

        graphService.detectAndFormCircles(locationId);

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(neo4jClient).query(queryCaptor.capture());
        String query = queryCaptor.getValue();
        assertTrue(query.contains("MATCH (u:User)-[r:ENCOUNTERED {locationId: $loc}]->(target:User)"));
        assertTrue(query.contains("r.duration > 300"));
        assertTrue(query.contains("size(users) >= 3"));
        assertTrue(query.contains("MERGE (c:Circle {locationId: $loc, isActive: true})"));
    }

    @Test
    void shouldBindLocationIdParameter() {
        UserNodeRepository userNodeRepository = mock(UserNodeRepository.class);
        Neo4jClient neo4jClient = mock(Neo4jClient.class);
        Neo4jClient.UnboundRunnableSpec runnableSpec = mock(Neo4jClient.UnboundRunnableSpec.class, RETURNS_DEEP_STUBS);

        when(neo4jClient.query(anyString())).thenReturn(runnableSpec);

        GraphService graphService = new GraphService(userNodeRepository, neo4jClient);
        String locationId = "cafeteria-2";

        graphService.detectAndFormCircles(locationId);

        verify(neo4jClient).query(anyString());
    }
}
