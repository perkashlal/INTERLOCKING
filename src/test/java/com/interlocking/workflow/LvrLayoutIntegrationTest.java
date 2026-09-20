package com.interlocking.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interlocking.api.dto.FindRouteRequest;
import com.interlocking.api.dto.ReleaseRouteRequest;
import com.interlocking.api.dto.SetOccupancyRequest;
import com.interlocking.api.dto.SimulateRouteRequest;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the full Main Workflow (SRS Section 9) against sample-layouts/lvr_1.xml,
 * the real DK-IXL interlocking export format the README says this project targets
 * (as opposed to the small synthetic simple-line.xml/junction-layout.xml fixtures
 * used elsewhere). lvr_1.xml also has a shape the synthetic layouts don't: a passing
 * loop where two parallel track sections (801/802) reconnect the same two points
 * (PM03U/PM04U), which is a good real-world check of FR-08 (points reserved as a
 * shared resource) and NFR-01 (a route can't use a point another route is holding),
 * distinct from just occupied/free track sections.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class LvrLayoutIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void loadOccupyRouteSimulateThenIssueASecondRouteAcrossTheLoop() throws Exception {
        mockMvc.perform(post("/api/layout").content(sampleLayout("lvr_1.xml")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/occupancy")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new SetOccupancyRequest(List.of("A593")))))
                .andExpect(status().isOk());

        // A593 -> A893 is the only path and crosses two points (PM01U, PM02U).
        mockMvc.perform(post("/api/route")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new FindRouteRequest("A593", "A893"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackSectionPath",
                        Matchers.contains("A593", "533", "PM01U", "083", "PM02U", "803", "A893")))
                .andExpect(jsonPath("$.pointsUsed", Matchers.containsInAnyOrder("PM01U", "PM02U")));
        mockMvc.perform(post("/api/route/simulate")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new SimulateRouteRequest(
                                List.of("A593", "533", "PM01U", "083", "PM02U", "803", "A893")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.occupiedTrackIds", Matchers.contains("A893")))
                .andExpect(jsonPath("$.reservedTrackIds").isEmpty());

        // FR-15/SPR-04: a second, iterative request from the new position crosses the passing
        // loop (PM02U/PM03U/PM04U) via whichever of 801/802 the route finder picks; either is a
        // valid, equally-short choice, so only the fixed points either way are asserted.
        MvcResult secondRoute = mockMvc.perform(post("/api/route")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new FindRouteRequest("A893", "A894"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackSectionPath[0]").value("A893"))
                .andExpect(jsonPath("$.trackSectionPath[7]").value("A894"))
                .andExpect(jsonPath("$.pointsUsed", Matchers.containsInAnyOrder("PM02U", "PM03U", "PM04U")))
                .andReturn();
        var routeResponse = objectMapper.readTree(secondRoute.getResponse().getContentAsString());
        assertThat(routeResponse.get("trackSectionPath")).hasSize(8);
        List<String> secondPath = objectMapper.convertValue(routeResponse.get("trackSectionPath"), List.class);

        mockMvc.perform(post("/api/route/simulate")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new SimulateRouteRequest(secondPath))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.occupiedTrackIds", Matchers.contains("A894")))
                .andExpect(jsonPath("$.reservedTrackIds").isEmpty());
    }

    @Test
    void sharedPointReservationBlocksASecondRouteEvenThoughItsOwnTrackIsFree() throws Exception {
        mockMvc.perform(post("/api/layout").content(sampleLayout("lvr_1.xml")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/occupancy")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new SetOccupancyRequest(List.of("A593", "534")))))
                .andExpect(status().isOk());

        // First train: A593 -> A893, holding point PM01U (and PM02U) for its route.
        mockMvc.perform(post("/api/route")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new FindRouteRequest("A593", "A893"))))
                .andExpect(status().isOk());

        // Second train sits on 534, an otherwise free and unreserved track section, but every
        // path from 534 leaves through PM01U - so this must be rejected (NFR-01/FR-08), not
        // because 534 itself is blocked, but because the point it needs is already held.
        mockMvc.perform(post("/api/route")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new FindRouteRequest("534", "A894"))))
                .andExpect(status().isBadRequest());

        // Releasing the first route's hold on the point frees it up for the second request.
        mockMvc.perform(post("/api/route/release")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ReleaseRouteRequest(
                                List.of("533", "PM01U", "083", "PM02U", "803", "A893")))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/route")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new FindRouteRequest("534", "A894"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pointsUsed", Matchers.containsInAnyOrder("PM01U", "PM02U", "PM03U", "PM04U")));
    }

    private String sampleLayout(String fileName) throws Exception {
        return Files.readString(Path.of("sample-layouts", fileName));
    }
}
