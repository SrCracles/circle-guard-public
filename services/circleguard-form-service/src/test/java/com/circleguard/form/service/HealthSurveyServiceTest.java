package com.circleguard.form.service;

import com.circleguard.form.metrics.BusinessMetrics;
import com.circleguard.form.model.HealthSurvey;
import com.circleguard.form.model.Questionnaire;
import com.circleguard.form.model.ValidationStatus;
import com.circleguard.form.repository.HealthSurveyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class HealthSurveyServiceTest {

    @Mock
    private HealthSurveyRepository repository;

    @Mock
    private QuestionnaireService questionnaireService;

    @Mock
    private SymptomMapper symptomMapper;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private BusinessMetrics businessMetrics;

    @InjectMocks
    private HealthSurveyService healthSurveyService;

    private HealthSurvey survey;
    private UUID anonymousId;

    @BeforeEach
    void setUp() {
        anonymousId = UUID.randomUUID();
        survey = new HealthSurvey();
        survey.setAnonymousId(anonymousId);
    }

    @Test
    void shouldSubmitSurveyAndEmitEventWithSymptoms() {
        Questionnaire questionnaire = new Questionnaire();
        when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.of(questionnaire));
        when(symptomMapper.hasSymptoms(survey, questionnaire)).thenReturn(true);
        when(repository.save(any(HealthSurvey.class))).thenAnswer(inv -> inv.getArgument(0));

        HealthSurvey result = healthSurveyService.submitSurvey(survey);

        assertThat(result.getHasFever()).isTrue();
        assertThat(result.getHasCough()).isTrue();
        verify(kafkaTemplate).send(eq("survey.submitted"), eq(anonymousId.toString()), any());
    }

    @Test
    void shouldSubmitSurveyWithoutSymptoms() {
        Questionnaire questionnaire = new Questionnaire();
        when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.of(questionnaire));
        when(symptomMapper.hasSymptoms(survey, questionnaire)).thenReturn(false);
        when(repository.save(any(HealthSurvey.class))).thenAnswer(inv -> inv.getArgument(0));

        HealthSurvey result = healthSurveyService.submitSurvey(survey);

        assertThat(result.getHasFever()).isFalse();
        assertThat(result.getHasCough()).isFalse();
    }

    @Test
    void shouldSubmitSurveyWithAttachmentAndSetPending() {
        survey.setAttachmentPath("/files/cert.pdf");
        when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.empty());
        when(repository.save(any(HealthSurvey.class))).thenAnswer(inv -> inv.getArgument(0));

        HealthSurvey result = healthSurveyService.submitSurvey(survey);

        assertThat(result.getValidationStatus()).isEqualTo(ValidationStatus.PENDING);
        verify(kafkaTemplate).send(eq("survey.submitted"), eq(anonymousId.toString()), any());
    }

    @Test
    void shouldNotOverrideExistingLegacyFields() {
        survey.setHasFever(true);
        survey.setHasCough(false);
        when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.empty());
        when(repository.save(any(HealthSurvey.class))).thenAnswer(inv -> inv.getArgument(0));

        HealthSurvey result = healthSurveyService.submitSurvey(survey);

        assertThat(result.getHasFever()).isTrue();
        assertThat(result.getHasCough()).isFalse();
    }

    @Test
    void shouldReturnPendingSurveys() {
        when(repository.findByAttachmentPathIsNotNullAndValidationStatus(ValidationStatus.PENDING))
                .thenReturn(List.of(survey));

        List<HealthSurvey> result = healthSurveyService.getPendingSurveys();

        assertThat(result).hasSize(1);
    }

    @Test
    void shouldApproveSurveyAndEmitCertificateEvent() {
        UUID surveyId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        survey.setValidationStatus(ValidationStatus.PENDING);

        when(repository.findById(surveyId)).thenReturn(Optional.of(survey));
        when(repository.save(any(HealthSurvey.class))).thenAnswer(inv -> inv.getArgument(0));

        healthSurveyService.validateSurvey(surveyId, ValidationStatus.APPROVED, adminId);

        assertThat(survey.getValidationStatus()).isEqualTo(ValidationStatus.APPROVED);
        assertThat(survey.getValidatedBy()).isEqualTo(adminId);
        verify(kafkaTemplate).send(eq("certificate.validated"), eq(anonymousId.toString()), any());
    }

    @Test
    void shouldRejectSurveyWithoutEmittingEvent() {
        UUID surveyId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        survey.setValidationStatus(ValidationStatus.PENDING);

        when(repository.findById(surveyId)).thenReturn(Optional.of(survey));
        when(repository.save(any(HealthSurvey.class))).thenAnswer(inv -> inv.getArgument(0));

        healthSurveyService.validateSurvey(surveyId, ValidationStatus.REJECTED, adminId);

        assertThat(survey.getValidationStatus()).isEqualTo(ValidationStatus.REJECTED);
        verify(kafkaTemplate, never()).send(eq("certificate.validated"), any(), any());
    }

    @Test
    void shouldThrowExceptionWhenSurveyNotFoundForValidation() {
        UUID surveyId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        when(repository.findById(surveyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> healthSurveyService.validateSurvey(surveyId, ValidationStatus.APPROVED, adminId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Survey not found");
    }
}
