package com.circleguard.form.controller;

import com.circleguard.form.model.ValidationStatus;
import com.circleguard.form.service.HealthSurveyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CertificateValidationController.class)
@AutoConfigureMockMvc(addFilters = false)
public class CertificateValidationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HealthSurveyService surveyService;

    @Test
    void shouldGetPendingSurveys() throws Exception {
        when(surveyService.getPendingSurveys()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/certificates/pending"))
                .andExpect(status().isOk());

        verify(surveyService).getPendingSurveys();
    }

    @Test
    void shouldValidateSurvey() throws Exception {
        UUID id = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/certificates/{id}/validate", id)
                        .param("status", ValidationStatus.APPROVED.name())
                        .param("adminId", adminId.toString()))
                .andExpect(status().isOk());

        verify(surveyService).validateSurvey(id, ValidationStatus.APPROVED, adminId);
    }
}
