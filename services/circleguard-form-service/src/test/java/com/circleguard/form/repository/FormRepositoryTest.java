package com.circleguard.form.repository;

import com.circleguard.form.model.HealthSurvey;
import com.circleguard.form.model.Questionnaire;
import com.circleguard.form.model.ValidationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
public class FormRepositoryTest {

    @Autowired
    private HealthSurveyRepository healthSurveyRepository;

    @Autowired
    private QuestionnaireRepository questionnaireRepository;

    @Test
    void shouldSaveAndRetrieveHealthSurvey() {
        HealthSurvey survey = HealthSurvey.builder()
                .anonymousId(UUID.randomUUID())
                .hasFever(true)
                .validationStatus(ValidationStatus.PENDING)
                .attachmentPath("/file.pdf")
                .build();

        HealthSurvey saved = healthSurveyRepository.save(survey);
        Optional<HealthSurvey> found = healthSurveyRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getHasFever()).isTrue();
    }

    @Test
    void shouldSaveAndRetrieveQuestionnaire() {
        Questionnaire q = Questionnaire.builder()
                .title("Daily")
                .version(1)
                .isActive(true)
                .build();

        Questionnaire saved = questionnaireRepository.save(q);
        Optional<Questionnaire> found = questionnaireRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("Daily");
    }

    @Test
    void shouldFindActiveQuestionnaire() {
        Questionnaire q = Questionnaire.builder()
                .title("Active Q")
                .version(2)
                .isActive(true)
                .build();
        questionnaireRepository.save(q);

        Optional<Questionnaire> active = questionnaireRepository.findFirstByIsActiveTrueOrderByVersionDesc();
        assertThat(active).isPresent();
        assertThat(active.get().getTitle()).isEqualTo("Active Q");
    }
}
