package com.circleguard.dashboard.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PromotionClientTest {

    @Mock
    private RestTemplate restTemplate;

    private PromotionClient client;

    @BeforeEach
    void setUp() {
        client = new PromotionClient(new RestTemplateBuilder());
        ReflectionTestUtils.setField(client, "promotionServiceUrl", "http://promotion:8088");
        ReflectionTestUtils.setField(client, "restTemplate", restTemplate);
    }

    @Test
    void shouldReturnHealthStatsSuccessfully() {
        Map<String, Object> stats = Map.of("active", 100, "suspect", 5);
        when(restTemplate.getForObject(eq("http://promotion:8088/api/v1/health-status/stats"), eq(Map.class)))
                .thenReturn(stats);

        Map<String, Object> result = client.getHealthStats();

        assertThat(result.get("active")).isEqualTo(100);
    }

    @Test
    void shouldReturnFallbackWhenServiceUnavailable() {
        when(restTemplate.getForObject(any(), eq(Map.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        Map<String, Object> result = client.getHealthStats();

        assertThat(result).containsKey("error");
        assertThat(result.get("error")).isEqualTo("Service unavailable");
    }

    @Test
    void shouldReturnDepartmentStatsSuccessfully() {
        Map<String, Object> stats = Map.of("department", "Engineering", "confirmed", 2);
        when(restTemplate.getForObject(
                eq("http://promotion:8088/api/v1/health-status/stats/department/Engineering"),
                eq(Map.class)
        )).thenReturn(stats);

        Map<String, Object> result = client.getHealthStatsByDepartment("Engineering");

        assertThat(result.get("department")).isEqualTo("Engineering");
    }

    @Test
    void shouldReturnFallbackForDepartmentWhenServiceUnavailable() {
        when(restTemplate.getForObject(any(), eq(Map.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        Map<String, Object> result = client.getHealthStatsByDepartment("Math");

        assertThat(result).containsKey("error");
        assertThat(result.get("department")).isEqualTo("Math");
    }
}
