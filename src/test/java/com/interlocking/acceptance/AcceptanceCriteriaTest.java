package com.interlocking.acceptance;

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
 * One test per row of the SRS's Table 10 (Acceptance Criteria, AC-01..AC-13),
 * exercised through the REST API exactly as a Reviewer (SRS Table 3) would check
 * the implementation against the specification. Each test is intentionally
 * self-contained and named after the criterion it proves, even where it overlaps
 * with a more granular unit test elsewhere, so this class alone is a traceable
 * record of "does the running system satisfy every acceptance criterion".
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AcceptanceCriteriaTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /** AC-01: A legal XML layout can be loaded successfully. */
    @Test
    void ac01_legalLayoutLoadsSuccessfully() throws Exception {
        mockMvc.perform(post("/api/layout").content(sampleLayout("simple-line.xml")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackSectionCount").value(5));
    }

    /** AC-02: The loaded layout produces an internal topology graph. */
    @Test
    void ac02_loadedLayoutProducesATopologyGraph() throws Exception {
        mockMvc.perform(post("/api/layout").content(sampleLayout("simple-line.xml")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.layoutLoaded").value(true))
                .andExpect(jsonPath("$.allTrackSectionIds", Matchers.containsInAnyOrder("T1", "T2", "T3", "T4", "T5")));
    }

    /** AC-03: The user can set initial occupancy on selected track sections. */
    @Test
    void ac03_userCanSetInitialOccupancy() throws Exception {
        mockMvc.perform(post("/api/layout").content(sampleLayout("simple-line.xml")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/occupancy")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new SetOccupancyRequest(List.of("T1", "T3")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.occupiedTrackIds", Matchers.containsInAnyOrder("T1", "T3")));
    }

    /** AC-04: The user can submit source and destination track values. */
    @Test
    void ac04_userCanSubmitSourceAndDestinationTrackValues() throws Exception {
        mockMvc.perform(post("/api/layout").content(sampleLayout("simple-line.xml")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/route")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new FindRouteRequest("T1", "T5"))))
                .andExpect(status().isOk());
    }

    /** AC-05: The system accepts a request only when a free unreserved route exists. */
    @Test
    void ac05_acceptsRequestOnlyWhenAFreeUnreservedRouteExists() throws Exception {
        mockMvc.perform(post("/api/layout").content(sampleLayout("simple-line.xml")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/route")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new FindRouteRequest("T1", "T5"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservedTrackIds", Matchers.contains("T2", "T3", "T4", "T5")));
    }

    /** AC-06: The system rejects a request when every possible route is blocked. */
    @Test
    void ac06_rejectsRequestWhenEveryPossibleRouteIsBlocked() throws Exception {
        mockMvc.perform(post("/api/layout").content(sampleLayout("simple-line.xml")))
                .andExpect(status().isOk());
        // simple-line.xml is a single linear path with no alternative route around T3.
        mockMvc.perform(post("/api/occupancy")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new SetOccupancyRequest(List.of("T3")))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/route")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new FindRouteRequest("T1", "T5"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    /** AC-07: A successful route reserves all route sections and points. */
    @Test
    void ac07_successfulRouteReservesAllSectionsAndPoints() throws Exception {
        mockMvc.perform(post("/api/layout").content(sampleLayout("junction-layout.xml")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/route")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new FindRouteRequest("T1", "T5"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservedTrackIds", Matchers.containsInAnyOrder("T2", "P1", "T3", "T5")))
                .andExpect(jsonPath("$.pointsUsed", Matchers.contains("P1")));

        mockMvc.perform(get("/api/state"))
                .andExpect(jsonPath("$.reservedTrackIds", Matchers.containsInAnyOrder("T2", "P1", "T3", "T5")));
    }

    /** AC-08: The movement simulation updates occupancy section by section. */
    @Test
    void ac08_movementSimulationUpdatesOccupancySectionBySection() throws Exception {
        mockMvc.perform(post("/api/layout").content(sampleLayout("simple-line.xml")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/occupancy")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new SetOccupancyRequest(List.of("T1")))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/route")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new FindRouteRequest("T1", "T5"))))
                .andExpect(status().isOk());

        // Section-by-section is verified at the unit level in MovementSimulatorTest; here we
        // confirm the end-to-end, API-visible effect: the whole path resolves to exactly one
        // occupied section (the destination) and nothing left reserved.
        mockMvc.perform(post("/api/route/simulate")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new SimulateRouteRequest(List.of("T1", "T2", "T3", "T4", "T5")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.occupiedTrackIds", Matchers.contains("T5")))
                .andExpect(jsonPath("$.reservedTrackIds").isEmpty());
    }

    /** AC-09: The updated occupancy state remains for the next request. */
    @Test
    void ac09_updatedOccupancyStateRemainsForTheNextRequest() throws Exception {
        mockMvc.perform(post("/api/layout").content(sampleLayout("simple-line.xml")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/occupancy")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new SetOccupancyRequest(List.of("T1")))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/route")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new FindRouteRequest("T1", "T3"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/route/simulate")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new SimulateRouteRequest(List.of("T1", "T2", "T3")))))
                .andExpect(status().isOk());

        // A brand new request against the persisted state (not the original empty layout)
        // must see the train now sitting on T3.
        mockMvc.perform(get("/api/state"))
                .andExpect(jsonPath("$.occupiedTrackIds", Matchers.contains("T3")));
        mockMvc.perform(post("/api/route")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new FindRouteRequest("T3", "T5"))))
                .andExpect(status().isOk());
    }

    /** AC-10: Failed route finding does not reset or corrupt the existing state. */
    @Test
    void ac10_failedRouteFindingDoesNotResetOrCorruptExistingState() throws Exception {
        mockMvc.perform(post("/api/layout").content(sampleLayout("junction-layout.xml")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/route")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new FindRouteRequest("T1", "T5"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/route")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new FindRouteRequest("T6", "T1"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/state"))
                .andExpect(jsonPath("$.reservedTrackIds", Matchers.containsInAnyOrder("T2", "P1", "T3", "T5")));
    }

    /** AC-11: Clear state starts a new scenario on the same layout. */
    @Test
    void ac11_clearStateStartsANewScenarioOnTheSameLayout() throws Exception {
        mockMvc.perform(post("/api/layout").content(sampleLayout("simple-line.xml")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/occupancy")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new SetOccupancyRequest(List.of("T3")))))
                .andExpect(status().isOk());
        // Blocked while T3 is occupied.
        mockMvc.perform(post("/api/route")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new FindRouteRequest("T1", "T5"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/state/clear"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.layoutLoaded").value(true))
                .andExpect(jsonPath("$.occupiedTrackIds").isEmpty());

        // The very same layout, now on a fresh scenario, must support the route that was
        // blocked a moment ago.
        mockMvc.perform(post("/api/route")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new FindRouteRequest("T1", "T5"))))
                .andExpect(status().isOk());
    }

    /** AC-12: Loading a new XML layout replaces the previous layout and state. */
    @Test
    void ac12_loadingANewLayoutReplacesThePreviousLayoutAndState() throws Exception {
        mockMvc.perform(post("/api/layout").content(sampleLayout("simple-line.xml")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/occupancy")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new SetOccupancyRequest(List.of("T1")))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/layout").content(sampleLayout("junction-layout.xml")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackSectionCount").value(7));

        mockMvc.perform(get("/api/state"))
                .andExpect(jsonPath("$.occupiedTrackIds").isEmpty())
                .andExpect(jsonPath("$.allTrackSectionIds", Matchers.hasSize(7)));
    }

    /**
     * AC-13: Loading a malformed or illegal XML layout file is rejected with a clear error
     * message, and no layout/state change occurs.
     */
    @Test
    void ac13_malformedLayoutIsRejectedWithClearErrorAndNoStateChange() throws Exception {
        mockMvc.perform(post("/api/layout").content(sampleLayout("simple-line.xml")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/occupancy")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new SetOccupancyRequest(List.of("T1")))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/layout").content(sampleLayout("malformed.xml")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        // The previously loaded layout and its state must be completely untouched.
        mockMvc.perform(get("/api/state"))
                .andExpect(jsonPath("$.layoutLoaded").value(true))
                .andExpect(jsonPath("$.allTrackSectionIds", Matchers.containsInAnyOrder("T1", "T2", "T3", "T4", "T5")))
                .andExpect(jsonPath("$.occupiedTrackIds", Matchers.contains("T1")));
    }

    private String sampleLayout(String fileName) throws Exception {
        return Files.readString(Path.of("sample-layouts", fileName));
    }
}
