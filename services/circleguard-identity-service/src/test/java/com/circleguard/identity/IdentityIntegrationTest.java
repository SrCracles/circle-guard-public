package com.circleguard.identity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class IdentityIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldMapIdentity() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/identities/map",
                Map.of("realIdentity", "test@uni.edu"),
                Map.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("anonymousId");
    }

    @Test
    void shouldRegisterVisitor() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/identities/visitor",
                Map.of("name", "Guest", "email", "guest@example.com", "reason_for_visit", "Meeting"),
                Map.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("anonymousId");
    }
}
