package com.circleguard.form.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class FormModelsTest {

    @Test
    void healthSurveyBuilderAndAccessors() {
        UUID id = UUID.randomUUID();
        UUID anon = UUID.randomUUID();
        UUID validated = UUID.randomUUID();
        LocalDate date = LocalDate.now();
        Map<String, Object> responses = Map.of("q1", "yes");

        HealthSurvey survey = HealthSurvey.builder()
                .id(id)
                .anonymousId(anon)
                .hasFever(true)
                .hasCough(false)
                .otherSymptoms("headache")
                .exposureDate(date)
                .responses(responses)
                .attachmentPath("/file.pdf")
                .validationStatus(ValidationStatus.PENDING)
                .validatedBy(validated)
                .build();

        assertThat(survey.getId()).isEqualTo(id);
        assertThat(survey.getAnonymousId()).isEqualTo(anon);
        assertThat(survey.getHasFever()).isTrue();
        assertThat(survey.getHasCough()).isFalse();
        assertThat(survey.getOtherSymptoms()).isEqualTo("headache");
        assertThat(survey.getExposureDate()).isEqualTo(date);
        assertThat(survey.getResponses()).isEqualTo(responses);
        assertThat(survey.getAttachmentPath()).isEqualTo("/file.pdf");
        assertThat(survey.getValidationStatus()).isEqualTo(ValidationStatus.PENDING);
        assertThat(survey.getValidatedBy()).isEqualTo(validated);
        assertThat(survey.toString()).contains("headache");
    }

    @Test
    void healthSurveySettersAndAccessors() {
        HealthSurvey survey = new HealthSurvey();
        UUID id = UUID.randomUUID();
        UUID anon = UUID.randomUUID();
        UUID validated = UUID.randomUUID();
        LocalDate date = LocalDate.now();
        Map<String, Object> responses = Map.of("k", "v");

        survey.setId(id);
        survey.setAnonymousId(anon);
        survey.setHasFever(true);
        survey.setHasCough(true);
        survey.setOtherSymptoms("symptom");
        survey.setExposureDate(date);
        survey.setResponses(responses);
        survey.setAttachmentPath("/path");
        survey.setValidationStatus(ValidationStatus.APPROVED);
        survey.setValidatedBy(validated);

        assertThat(survey.getId()).isEqualTo(id);
        assertThat(survey.getAnonymousId()).isEqualTo(anon);
        assertThat(survey.getHasFever()).isTrue();
        assertThat(survey.getHasCough()).isTrue();
        assertThat(survey.getOtherSymptoms()).isEqualTo("symptom");
        assertThat(survey.getExposureDate()).isEqualTo(date);
        assertThat(survey.getResponses()).isEqualTo(responses);
        assertThat(survey.getAttachmentPath()).isEqualTo("/path");
        assertThat(survey.getValidationStatus()).isEqualTo(ValidationStatus.APPROVED);
        assertThat(survey.getValidatedBy()).isEqualTo(validated);
    }

    @Test
    void healthSurveyEqualsAndHashCode() {
        HealthSurvey s1 = HealthSurvey.builder().otherSymptoms("a").build();
        HealthSurvey s2 = HealthSurvey.builder().otherSymptoms("a").build();
        HealthSurvey s3 = HealthSurvey.builder().otherSymptoms("b").build();

        assertThat(s1).isEqualTo(s2);
        assertThat(s1.hashCode()).isEqualTo(s2.hashCode());
        assertThat(s1).isNotEqualTo(s3);
        assertThat(s1).isNotEqualTo(null);
        assertThat(s1).isNotEqualTo("string");
    }

    @Test
    void questionnaireBuilderAndAccessors() {
        Questionnaire q = Questionnaire.builder()
                .title("Daily")
                .description("Check symptoms")
                .version(1)
                .isActive(true)
                .build();

        assertThat(q.getTitle()).isEqualTo("Daily");
        assertThat(q.getDescription()).isEqualTo("Check symptoms");
        assertThat(q.getVersion()).isEqualTo(1);
        assertThat(q.getIsActive()).isTrue();
        assertThat(q.toString()).contains("Daily");
    }

    @Test
    void questionnaireSettersAndAccessors() {
        Questionnaire q = new Questionnaire();
        q.setTitle("T");
        q.setDescription("D");
        q.setVersion(2);
        q.setIsActive(false);

        assertThat(q.getTitle()).isEqualTo("T");
        assertThat(q.getDescription()).isEqualTo("D");
        assertThat(q.getVersion()).isEqualTo(2);
        assertThat(q.getIsActive()).isFalse();
    }

    @Test
    void questionnaireEqualsAndHashCode() {
        Questionnaire q1 = Questionnaire.builder().title("A").build();
        Questionnaire q2 = Questionnaire.builder().title("A").build();
        Questionnaire q3 = Questionnaire.builder().title("B").build();

        assertThat(q1).isEqualTo(q2);
        assertThat(q1.hashCode()).isEqualTo(q2.hashCode());
        assertThat(q1).isNotEqualTo(q3);
        assertThat(q1).isNotEqualTo(null);
        assertThat(q1).isNotEqualTo("string");
    }

    @Test
    void questionBuilderAndAccessors() {
        Questionnaire parent = Questionnaire.builder().title("P").build();
        UUID id = UUID.randomUUID();
        Question q = Question.builder()
                .id(id)
                .text("Do you cough?")
                .type(QuestionType.YES_NO)
                .options("{}")
                .orderIndex(1)
                .questionnaire(parent)
                .build();

        assertThat(q.getId()).isEqualTo(id);
        assertThat(q.getText()).isEqualTo("Do you cough?");
        assertThat(q.getType()).isEqualTo(QuestionType.YES_NO);
        assertThat(q.getOptions()).isEqualTo("{}");
        assertThat(q.getOrderIndex()).isEqualTo(1);
        assertThat(q.getQuestionnaire()).isEqualTo(parent);
        assertThat(q.toString()).contains("Do you cough?");
    }

    @Test
    void questionSettersAndAccessors() {
        Question q = new Question();
        UUID id = UUID.randomUUID();
        Questionnaire parent = Questionnaire.builder().title("P").build();

        q.setId(id);
        q.setText("text");
        q.setType(QuestionType.TEXT);
        q.setOptions("[]");
        q.setOrderIndex(2);
        q.setQuestionnaire(parent);

        assertThat(q.getId()).isEqualTo(id);
        assertThat(q.getText()).isEqualTo("text");
        assertThat(q.getType()).isEqualTo(QuestionType.TEXT);
        assertThat(q.getOptions()).isEqualTo("[]");
        assertThat(q.getOrderIndex()).isEqualTo(2);
        assertThat(q.getQuestionnaire()).isEqualTo(parent);
    }

    @Test
    void questionEqualsAndHashCode() {
        Question q1 = Question.builder().text("a").build();
        Question q2 = Question.builder().text("a").build();
        Question q3 = Question.builder().text("b").build();

        assertThat(q1).isEqualTo(q2);
        assertThat(q1.hashCode()).isEqualTo(q2.hashCode());
        assertThat(q1).isNotEqualTo(q3);
        assertThat(q1).isNotEqualTo(null);
        assertThat(q1).isNotEqualTo("string");
    }

    @Test
    void validationStatusValues() {
        assertThat(ValidationStatus.values()).contains(ValidationStatus.PENDING, ValidationStatus.APPROVED, ValidationStatus.REJECTED);
        assertThat(ValidationStatus.valueOf("PENDING")).isEqualTo(ValidationStatus.PENDING);
    }

    @Test
    void questionTypeValues() {
        assertThat(QuestionType.values()).contains(QuestionType.YES_NO, QuestionType.SINGLE_CHOICE, QuestionType.MULTI_CHOICE, QuestionType.TEXT);
        assertThat(QuestionType.valueOf("YES_NO")).isEqualTo(QuestionType.YES_NO);
    }
}
