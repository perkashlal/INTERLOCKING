package com.interlocking.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interlocking.api.dto.FindRouteRequest;
import com.interlocking.api.dto.SetOccupancyRequest;
import com.interlocking.api.dto.SimulateRouteRequest;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the full Main Workflow from the SRS (Section 9) end-to-end through the
 * REST API, as a single scenario user issuing iterative requests would (FR-15).
 * Individual endpoints already have their own focused tests; these prove the
 * pieces compose correctly and that state persistence rules (Section 7) hold up
 * across a realistic sequence of calls, not just in isolation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class FullWorkflowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void loadOccupyRouteSimulateThenIssueASecondRouteFromTheNewPosition() throws Exception {
        mockMvc.perform(post("/api/layout").content(sampleLayout("simple-line.xml")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/occupancy")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new SetOccupancyRequest(List.of("T1")))))
                .andExpect(status().isOk());

        // First movement: T1 -> T3.
        mockMvc.perform(post("/api/route")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new FindRouteRequest("T1", "T3"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackSectionPath", Matchers.contains("T1", "T2", "T3")));
        mockMvc.perform(post("/api/route/simulate")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new SimulateRouteRequest(List.of("T1", "T2", "T3")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.occupiedTrackIds", Matchers.contains("T3")));

        // FR-15/SPR-04: the second request must use the current persisted state (train now
        // sitting on T3), not the original empty layout - so T3 -> T5 must work even though
        // T1/T2 were never re-occupied.
        mockMvc.perform(post("/api/route")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new FindRouteRequest("T3", "T5"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackSectionPath", Matchers.contains("T3", "T4", "T5")));
        mockMvc.perform(post("/api/route/simulate")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new SimulateRouteRequest(List.of("T3", "T4", "T5")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.occupiedTrackIds", Matchers.contains("T5")))
                .andExpect(jsonPath("$.reservedTrackIds").isEmpty());
    }

    @Test
    void failedRouteRequestLeavesExistingReservationsUntouched() throws Exception {
        mockMvc.perform(post("/api/layout").content(sampleLayout("junction-layout.xml")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/route")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new FindRouteRequest("T1", "T5"))))
                .andExpect(status().isOk());

        // T6 -> T1 would have to cross the already-reserved P1/T2, so it must fail...
        mockMvc.perform(post("/api/route")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new FindRouteRequest("T6", "T1"))))
                .andExpect(status().isBadRequest());

        // ...and AC-10: the state from the first, successful reservation must be untouched.
        mockMvc.perform(get("/api/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservedTrackIds", Matchers.containsInAnyOrder("T2", "P1", "T3", "T5")));
    }

    @Test
    void clearStateKeepsLayoutAndLetsAFreshScenarioReuseAllTrackSections() throws Exception {
        mockMvc.perform(post("/api/layout").content(sampleLayout("simple-line.xml")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/occupancy")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new SetOccupancyRequest(List.of("T1", "T5")))))
                .andExpect(status().isOk());

        // FR-16/SPR-05: clearing removes occupancy/reservation but keeps the layout.
        mockMvc.perform(post("/api/state/clear"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.layoutLoaded").value(true))
                .andExpect(jsonPath("$.allTrackSectionIds", Matchers.hasSize(5)))
                .andExpect(jsonPath("$.occupiedTrackIds").isEmpty());

        // A track section that was occupied before the clear must be routable again now.
        mockMvc.perform(post("/api/route")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new FindRouteRequest("T1", "T5"))))
                .andExpect(status().isOk());
    }

    @Test
    void loadingANewLayoutReplacesBothThePreviousLayoutAndItsState() throws Exception {
        mockMvc.perform(post("/api/layout").content(sampleLayout("simple-line.xml")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/occupancy")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new SetOccupancyRequest(List.of("T1")))))
                .andExpect(status().isOk());

        // FR-17/SPR-06: loading a new (structurally different) layout replaces layout AND state.
        mockMvc.perform(post("/api/layout").content(sampleLayout("junction-layout.xml")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackSectionCount").value(7))
                .andExpect(jsonPath("$.pointCount").value(1));

        mockMvc.perform(get("/api/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.occupiedTrackIds").isEmpty())
                .andExpect(jsonPath("$.reservedTrackIds").isEmpty())
                .andExpect(jsonPath("$.allTrackSectionIds", Matchers.hasSize(7)));

        // The old layout's T1 (with no neighbors defined) must not leak into the new one;
        // a route the new layout actually supports must work.
        mockMvc.perform(post("/api/route")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new FindRouteRequest("T1", "T6"))))
                .andExpect(status().isOk());
    }

    private String sampleLayout(String fileName) throws Exception {
        return Files.readString(Path.of("sample-layouts", fileName));
    }
}
