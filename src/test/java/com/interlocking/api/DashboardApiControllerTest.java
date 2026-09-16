package com.interlocking.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interlocking.api.dto.FindRouteRequest;
import com.interlocking.api.dto.ReleaseRouteRequest;
import com.interlocking.api.dto.SetOccupancyRequest;
import org.hamcrest.Matchers;
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

    @Test
    void findsRouteAndReservesItExcludingOccupiedOrigin() throws Exception {
        mockMvc.perform(post("/api/layout").content(sampleLayout("simple-line.xml")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/occupancy")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new SetOccupancyRequest(List.of("T1")))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/route")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new FindRouteRequest("T1", "T5"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackSectionPath", Matchers.contains("T1", "T2", "T3", "T4", "T5")))
                .andExpect(jsonPath("$.reservedTrackIds", Matchers.contains("T2", "T3", "T4", "T5")))
                .andExpect(jsonPath("$.pointsUsed").isEmpty());

        mockMvc.perform(get("/api/state"))
                .andExpect(jsonPath("$.reservedTrackIds", Matchers.contains("T2", "T3", "T4", "T5")));
    }

    @Test
    void findRouteResolvesMarkerboardIds() throws Exception {
        mockMvc.perform(post("/api/layout").content(sampleLayout("simple-line.xml")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/route")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new FindRouteRequest("M1", "M2"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackSectionPath", Matchers.contains("T1", "T2", "T3", "T4", "T5")));
    }

    @Test
    void findRouteReportsPointsUsedOnAJunctionLayout() throws Exception {
        mockMvc.perform(post("/api/layout").content(sampleLayout("junction-layout.xml")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/route")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new FindRouteRequest("T1", "T5"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackSectionPath", Matchers.contains("T1", "T2", "P1", "T3", "T5")))
                .andExpect(jsonPath("$.pointsUsed", Matchers.contains("P1")));
    }

    @Test
    void secondRouteCannotOverlapAnAlreadyReservedSection() throws Exception {
        mockMvc.perform(post("/api/layout").content(sampleLayout("junction-layout.xml")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/route")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new FindRouteRequest("T1", "T5"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/route")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new FindRouteRequest("T5", "T1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void releaseRouteFreesReservedSectionsForReuse() throws Exception {
        mockMvc.perform(post("/api/layout").content(sampleLayout("simple-line.xml")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/route")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new FindRouteRequest("T1", "T5"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/route/release")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ReleaseRouteRequest(List.of("T2", "T3", "T4", "T5")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservedTrackIds").isEmpty());
    }

    @Test
    void findRouteFailsWhenNoLayoutIsLoaded() throws Exception {
        mockMvc.perform(post("/api/route")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new FindRouteRequest("T1", "T5"))))
                .andExpect(status().isBadRequest());
    }

    private String sampleLayout(String fileName) throws Exception {
        return Files.readString(Path.of("sample-layouts", fileName));
    }
}
