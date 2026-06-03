package com.circleguard.dashboard.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

class KAnonymityFilterTest {

    private KAnonymityFilter kAnonymityFilter;

    @BeforeEach
    void setUp() {
        kAnonymityFilter = new KAnonymityFilter();
    }

    @Test
    void testApplyFilterWhenCountIsBelowThreshold() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("activeCount", 3);
        stats.put("department", "Engineering");

        Map<String, Object> result = kAnonymityFilter.apply(stats);

        assertEquals("<5", result.get("activeCount"));
        assertEquals("Engineering", result.get("department"));
    }

    @Test
    void testApplyFilterWhenCountIsAboveThreshold() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("activeCount", 10);
        stats.put("department", "Engineering");

        Map<String, Object> result = kAnonymityFilter.apply(stats);

        assertEquals(10, result.get("activeCount"));
    }
}
