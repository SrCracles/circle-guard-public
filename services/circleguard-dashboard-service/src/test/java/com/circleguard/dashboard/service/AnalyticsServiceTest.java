package com.circleguard.dashboard.service;

import com.circleguard.dashboard.client.PromotionClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private PromotionClient promotionClient;

    @Mock
    private KAnonymityFilter kAnonymityFilter;

    private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        analyticsService = new AnalyticsService(jdbcTemplate, promotionClient, kAnonymityFilter);
    }

    @Test
    void testGetCampusSummary() {
        Map<String, Object> expected = Map.of("totalGreen", 1000);
        when(promotionClient.getHealthStats()).thenReturn(expected);

        Map<String, Object> result = analyticsService.getCampusSummary();
        assertEquals(expected, result);
    }

    @Test
    void testGetDepartmentStats() {
        Map<String, Object> raw = Map.of("totalGreen", 50);
        Map<String, Object> filtered = Map.of("totalGreen", 50, "note", "k-anonymized");

        when(promotionClient.getHealthStatsByDepartment("Engineering")).thenReturn(raw);
        when(kAnonymityFilter.apply(raw)).thenReturn(filtered);

        Map<String, Object> result = analyticsService.getDepartmentStats("Engineering");
        assertEquals(filtered, result);
    }

    @Test
    void testGetEntryTrends() {
        UUID locationId = UUID.randomUUID();
        List<Map<String, Object>> mockRows = new ArrayList<>();
        Map<String, Object> row1 = new HashMap<>();
        row1.put("hour", new Date());
        row1.put("entry_count", 10L);
        
        Map<String, Object> row2 = new HashMap<>();
        row2.put("hour", new Date());
        row2.put("entry_count", 3L);
        
        mockRows.add(row1);
        mockRows.add(row2);

        when(jdbcTemplate.queryForList(anyString(), eq(locationId))).thenReturn(mockRows);

        List<Map<String, Object>> result = analyticsService.getEntryTrends(locationId);

        assertEquals(2, result.size());
        assertEquals(10L, result.get(0).get("entry_count"));
        assertEquals("<5", result.get(1).get("entry_count"));
    }

    @Test
    void testGetTimeSeriesFallback() {
        when(jdbcTemplate.queryForList(anyString(), eq(10))).thenThrow(new RuntimeException("Table not found"));

        List<Map<String, Object>> result = analyticsService.getTimeSeries("hourly", 10);

        assertFalse(result.isEmpty());
        // Fallback generates 10 * 4 = 40 rows maximum, or 24 * 4 = 96 rows maximum.
        assertEquals(40, result.size()); 
    }
}
