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
 * Exercises the full SRS Main Workflow (Section 9) against {@code lvr_1.xml}, the
 * real DK-IXL interlocking export format the README calls out as the format this
 * project targets (SRS "Main input"). {@link com.interlocking.parser.XmlLayoutParserTest}
 * already proves this file parses correctly; this class proves the *rest* of the
 * system - route finding through points, markerboard-based requests, reservation,
 * simulation, and re-routing around an occupied alternative - also works on it, not
 * just on the small synthetic layouts used elsewhere.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RealLayoutWorkflowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void loadsLayoutAndFindsRouteThroughTwoPointsUsingMarkerboards() throws Exception {
        mockMvc.perform(post("/api/layout").content(sampleLayout("lvr_1.xml")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackSectionCount").value(15))
                .andExpect(jsonPath("$.pointCount").value(4));

        // AU593/AU893 are markerboards mounted on A593/A893 (SRS Section 6: source and
        // destination may be selected via markerboard, resolved to the underlying track).
        mockMvc.perform(post("/api/route")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new FindRouteRequest("AU593", "AU893"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackSectionPath",
                        Matchers.contains("A593", "533", "PM01U", "083", "PM02U", "803", "A893")))
                .andExpect(jsonPath("$.pointsUsed", Matchers.contains("PM01U", "PM02U")));
    }

    @Test
    void simulatesMovementThenIssuesASecondIterativeRequestReusingASharedPoint() throws Exception {
        mockMvc.perform(post("/api/layout").content(sampleLayout("lvr_1.xml")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/route")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new FindRouteRequest("A593", "A893"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/route/simulate")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new SimulateRouteRequest(
                                List.of("A593", "533", "PM01U", "083", "PM02U", "803", "A893")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.occupiedTrackIds", Matchers.contains("A893")))
                .andExpect(jsonPath("$.reservedTrackIds").isEmpty());

        // FR-15/SPR-04: a second, unrelated request against the persisted state must be
        // able to reuse PM01U and 083, which the first route reserved and then freed again
        // once movement finished - it is not left permanently locked.
        mockMvc.perform(post("/api/route")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new FindRouteRequest("A594", "A894"))))
                .andExpect(status().isOk())
                // 801 and 802 are parallel, equal-length sections between PM03U and PM04U;
                // either is a valid shortest route, so only assert the point sequence used.
                .andExpect(jsonPath("$.pointsUsed", Matchers.contains("PM01U", "PM02U", "PM03U", "PM04U")));
    }

    @Test
    void reRoutesAroundAnOccupiedParallelTrackBetweenTheSameTwoPoints() throws Exception {
        mockMvc.perform(post("/api/layout").content(sampleLayout("lvr_1.xml")))
                .andExpect(status().isOk());

        // PM03U and PM04U are connected by two parallel sections, 801 and 802. With 802
        // occupied, the only remaining free route must go via 801 instead (FR-07/NFR-01).
        mockMvc.perform(post("/api/occupancy")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new SetOccupancyRequest(List.of("802")))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/route")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new FindRouteRequest("083", "A894"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackSectionPath",
                        Matchers.contains("083", "PM02U", "PM03U", "801", "PM04U", "804", "A894")))
                .andExpect(jsonPath("$.reservedTrackIds", Matchers.not(Matchers.hasItem("802"))));
    }

    @Test
    void rejectsRequestWhenBothParallelTracksBetweenTheSamePointsAreBlocked() throws Exception {
        mockMvc.perform(post("/api/layout").content(sampleLayout("lvr_1.xml")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/occupancy")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new SetOccupancyRequest(List.of("801", "802")))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/route")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new FindRouteRequest("083", "A894"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        // AC-10: the occupancy set just above must still be exactly what was requested.
        mockMvc.perform(get("/api/state"))
                .andExpect(jsonPath("$.occupiedTrackIds", Matchers.containsInAnyOrder("801", "802")));
    }

    private String sampleLayout(String fileName) throws Exception {
        return Files.readString(Path.of("sample-layouts", fileName));
    }
}
