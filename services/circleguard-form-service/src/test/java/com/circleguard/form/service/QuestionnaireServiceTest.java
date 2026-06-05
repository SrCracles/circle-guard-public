package com.circleguard.form.service;

import com.circleguard.form.model.Question;
import com.circleguard.form.model.Questionnaire;
import com.circleguard.form.repository.QuestionnaireRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class QuestionnaireServiceTest {

    @Mock
    private QuestionnaireRepository repository;

    @InjectMocks
    private QuestionnaireService service;

    private Questionnaire questionnaire;

    @BeforeEach
    void setUp() {
        questionnaire = new Questionnaire();
        questionnaire.setId(UUID.randomUUID());
        questionnaire.setTitle("Daily Check");
        questionnaire.setIsActive(true);
    }

    @Test
    void shouldReturnAllQuestionnaires() {
        when(repository.findAll()).thenReturn(List.of(questionnaire));

        List<Questionnaire> result = service.getAllQuestionnaires();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Daily Check");
    }

    @Test
    void shouldReturnActiveQuestionnaire() {
        when(repository.findFirstByIsActiveTrueOrderByVersionDesc()).thenReturn(Optional.of(questionnaire));

        Optional<Questionnaire> result = service.getActiveQuestionnaire();

        assertThat(result).isPresent();
        assertThat(result.get().getIsActive()).isTrue();
    }

    @Test
    void shouldSaveQuestionnaireAndLinkQuestions() {
        Question q = new Question();
        q.setText("Do you have symptoms?");
        questionnaire.setQuestions(List.of(q));

        when(repository.save(any(Questionnaire.class))).thenReturn(questionnaire);

        Questionnaire saved = service.saveQuestionnaire(questionnaire);

        assertThat(saved.getQuestions()).hasSize(1);
        assertThat(saved.getQuestions().get(0).getQuestionnaire()).isEqualTo(saved);
        verify(repository).save(questionnaire);
    }

    @Test
    void shouldActivateQuestionnaireAndDeactivateOthers() {
        UUID targetId = UUID.randomUUID();
        Questionnaire other = new Questionnaire();
        other.setId(UUID.randomUUID());
        other.setIsActive(true);

        Questionnaire target = new Questionnaire();
        target.setId(targetId);
        target.setIsActive(false);

        when(repository.findAll()).thenReturn(List.of(other, target));
        when(repository.findById(targetId)).thenReturn(Optional.of(target));

        service.activateQuestionnaire(targetId);

        assertThat(other.getIsActive()).isFalse();
        assertThat(target.getIsActive()).isTrue();
        verify(repository).save(other);
        verify(repository).save(target);
    }

    @Test
    void shouldHandleNullQuestionsWhenSaving() {
        questionnaire.setQuestions(null);
        when(repository.save(questionnaire)).thenReturn(questionnaire);

        Questionnaire saved = service.saveQuestionnaire(questionnaire);

        assertThat(saved).isNotNull();
        verify(repository).save(questionnaire);
    }
}
