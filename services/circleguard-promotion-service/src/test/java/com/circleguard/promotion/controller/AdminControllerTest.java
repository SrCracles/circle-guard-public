package com.circleguard.promotion.controller;

import com.circleguard.promotion.model.jpa.SystemSettings;
import com.circleguard.promotion.repository.jpa.SystemSettingsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SystemSettingsRepository settingsRepository;

    @Test
    void getSettings_ReturnsDefaultWhenEmpty() throws Exception {
        when(settingsRepository.getSettings()).thenReturn(Optional.empty());
        when(settingsRepository.save(any(SystemSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(get("/api/v1/admin/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unconfirmedFencingEnabled").value(true))
                .andExpect(jsonPath("$.autoThresholdSeconds").value(3600))
                .andExpect(jsonPath("$.mandatoryFenceDays").value(14))
                .andExpect(jsonPath("$.encounterWindowDays").value(14));
    }

    @Test
    void getSettings_ReturnsExisting() throws Exception {
        SystemSettings settings = SystemSettings.builder()
                .id(1L)
                .unconfirmedFencingEnabled(false)
                .autoThresholdSeconds(100L)
                .mandatoryFenceDays(5)
                .encounterWindowDays(5)
                .build();

        when(settingsRepository.getSettings()).thenReturn(Optional.of(settings));

        mockMvc.perform(get("/api/v1/admin/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unconfirmedFencingEnabled").value(false))
                .andExpect(jsonPath("$.autoThresholdSeconds").value(100));
    }

    @Test
    void updateSettings_Success() throws Exception {
        SystemSettings existing = SystemSettings.builder()
                .id(1L)
                .unconfirmedFencingEnabled(true)
                .autoThresholdSeconds(3600L)
                .mandatoryFenceDays(14)
                .encounterWindowDays(14)
                .build();

        when(settingsRepository.getSettings()).thenReturn(Optional.of(existing));
        when(settingsRepository.save(any(SystemSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SystemSettings request = SystemSettings.builder()
                .unconfirmedFencingEnabled(false)
                .autoThresholdSeconds(1800L)
                .mandatoryFenceDays(7)
                .encounterWindowDays(7)
                .build();

        mockMvc.perform(post("/api/v1/admin/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unconfirmedFencingEnabled").value(false))
                .andExpect(jsonPath("$.autoThresholdSeconds").value(1800))
                .andExpect(jsonPath("$.mandatoryFenceDays").value(7))
                .andExpect(jsonPath("$.encounterWindowDays").value(7));
    }

    @Test
    void toggleUnconfirmedFencing_Success() throws Exception {
        SystemSettings existing = SystemSettings.builder()
                .id(1L)
                .unconfirmedFencingEnabled(false)
                .autoThresholdSeconds(3600L)
                .mandatoryFenceDays(14)
                .encounterWindowDays(14)
                .build();

        when(settingsRepository.getSettings()).thenReturn(Optional.of(existing));
        when(settingsRepository.save(any(SystemSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/v1/admin/settings/toggle-unconfirmed-fencing")
                        .param("enabled", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unconfirmedFencingEnabled").value(true));
    }
}
