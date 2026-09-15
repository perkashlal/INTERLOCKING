package com.interlocking.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interlocking.api.dto.SetOccupancyRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DashboardApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void stateBeforeAnyLayoutIsLoaded() throws Exception {
        mockMvc.perform(get("/api/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.layoutLoaded").value(false));
    }

    @Test
    void loadsLegalLayoutAndReportsCounts() throws Exception {
        mockMvc.perform(post("/api/layout").content(sampleLayout("simple-line.xml")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackSectionCount").value(5))
                .andExpect(jsonPath("$.pointCount").value(0))
                .andExpect(jsonPath("$.markerboardCount").value(2));
    }

    @Test
    void rejectsMalformedLayoutWithClearError() throws Exception {
        mockMvc.perform(post("/api/layout").content(sampleLayout("malformed.xml")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void setsOccupancyAfterLayoutLoaded() throws Exception {
        mockMvc.perform(post("/api/layout").content(sampleLayout("simple-line.xml")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/occupancy")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new SetOccupancyRequest(List.of("T1")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.occupiedTrackIds[0]").value("T1"));
    }

    @Test
    void setOccupancyRejectsUnknownTrackSection() throws Exception {
        mockMvc.perform(post("/api/layout").content(sampleLayout("simple-line.xml")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/occupancy")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new SetOccupancyRequest(List.of("GHOST")))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void clearStateKeepsLayoutButResetsOccupancy() throws Exception {
        mockMvc.perform(post("/api/layout").content(sampleLayout("simple-line.xml")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/occupancy")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new SetOccupancyRequest(List.of("T1")))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/state/clear"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.layoutLoaded").value(true))
                .andExpect(jsonPath("$.occupiedTrackIds").isEmpty());
    }

    private String sampleLayout(String fileName) throws Exception {
        return Files.readString(Path.of("sample-layouts", fileName));
    }
}
