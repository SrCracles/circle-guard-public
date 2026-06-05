package com.circleguard.form;

import com.circleguard.form.model.Questionnaire;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class FormIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldGetQuestionnaires() {
        ResponseEntity<Questionnaire[]> response = restTemplate.getForEntity("/api/v1/questionnaires", Questionnaire[].class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void shouldGetActiveQuestionnaire() {
        ResponseEntity<Questionnaire> response = restTemplate.getForEntity("/api/v1/questionnaires/active", Questionnaire.class);
        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.NOT_FOUND);
    }
}
