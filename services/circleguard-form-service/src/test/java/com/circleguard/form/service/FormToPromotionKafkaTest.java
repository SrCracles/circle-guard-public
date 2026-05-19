package com.circleguard.form.service;

import com.circleguard.form.model.HealthSurvey;
import com.circleguard.form.model.Question;
import com.circleguard.form.model.Questionnaire;
import com.circleguard.form.model.ValidationStatus;
import com.circleguard.form.repository.HealthSurveyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FormToPromotionKafkaTest {

    @InjectMocks
    private HealthSurveyService healthSurveyService;

    @Mock
    private HealthSurveyRepository repository;

    @Mock
    private QuestionnaireService questionnaireService;

    @Mock
    private SymptomMapper symptomMapper;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void submitSurvey_WithSymptoms_EmitsSurveySubmittedEvent() {
        UUID anonymousId = UUID.randomUUID();
        HealthSurvey survey = HealthSurvey.builder()
                .anonymousId(anonymousId)
                .responses(Map.of("q1", "YES"))
                .build();

        Questionnaire questionnaire = Questionnaire.builder()
                .questions(List.of(Question.builder().id(UUID.randomUUID()).text("fever").build()))
                .build();

        when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.of(questionnaire));
        when(symptomMapper.hasSymptoms(survey, questionnaire)).thenReturn(true);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HealthSurvey result = healthSurveyService.submitSurvey(survey);

        assertNotNull(result);
        assertTrue(result.getHasFever());
        verify(kafkaTemplate).send(eq("survey.submitted"), eq(anonymousId.toString()), argThat(event -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> ev = (Map<String, Object>) event;
            return ev.get("anonymousId").equals(anonymousId) && Boolean.TRUE.equals(ev.get("hasSymptoms"));
        }));
    }

    @Test
    void submitSurvey_WithoutSymptoms_EmitsEventWithFalseSymptoms() {
        UUID anonymousId = UUID.randomUUID();
        HealthSurvey survey = HealthSurvey.builder()
                .anonymousId(anonymousId)
                .responses(Map.of("q1", "NO"))
                .build();

        Questionnaire questionnaire = Questionnaire.builder()
                .questions(List.of(Question.builder().id(UUID.randomUUID()).text("fever").build()))
                .build();

        when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.of(questionnaire));
        when(symptomMapper.hasSymptoms(survey, questionnaire)).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HealthSurvey result = healthSurveyService.submitSurvey(survey);

        assertNotNull(result);
        assertFalse(result.getHasFever());
        verify(kafkaTemplate).send(eq("survey.submitted"), eq(anonymousId.toString()), argThat(event -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> ev = (Map<String, Object>) event;
            return ev.get("anonymousId").equals(anonymousId) && Boolean.FALSE.equals(ev.get("hasSymptoms"));
        }));
    }

    @Test
    void validateSurvey_Approved_EmitsCertificateValidatedEvent() {
        UUID surveyId = UUID.randomUUID();
        UUID anonymousId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        HealthSurvey survey = HealthSurvey.builder()
                .id(surveyId)
                .anonymousId(anonymousId)
                .validationStatus(ValidationStatus.PENDING)
                .build();

        when(repository.findById(surveyId)).thenReturn(Optional.of(survey));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        healthSurveyService.validateSurvey(surveyId, ValidationStatus.APPROVED, adminId);

        verify(kafkaTemplate).send(eq("certificate.validated"), eq(anonymousId.toString()), argThat(event -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> ev = (Map<String, Object>) event;
            return ev.get("anonymousId").equals(anonymousId) && "APPROVED".equals(ev.get("status"));
        }));
    }
}
