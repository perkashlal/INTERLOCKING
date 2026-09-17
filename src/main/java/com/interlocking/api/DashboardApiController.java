package com.interlocking.api;

import com.interlocking.api.dto.FindRouteRequest;
import com.interlocking.api.dto.LoadLayoutResponse;
import com.interlocking.api.dto.ReleaseRouteRequest;
import com.interlocking.api.dto.RouteResponse;
import com.interlocking.api.dto.SetOccupancyRequest;
import com.interlocking.api.dto.StateSnapshotResponse;
import com.interlocking.model.LayoutGraph;
import com.interlocking.model.TrackSection;
import com.interlocking.parser.XmlLayoutParser;
import com.interlocking.route.RouteResult;
import com.interlocking.route.RouteService;
import com.interlocking.state.ScenarioStateManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * REST facade for the dashboard (SRS Fig. 3/4): load layout, set occupancy, find
 * and reserve routes, release reservations, inspect state, and clear state.
 * Movement simulation is added once MovementSimulator exists.
 */
@RestController
@RequestMapping("/api")
public class DashboardApiController {

    private final XmlLayoutParser layoutParser;
    private final ScenarioStateManager stateManager;
    private final RouteService routeService;

    public DashboardApiController(XmlLayoutParser layoutParser, ScenarioStateManager stateManager,
                                   RouteService routeService) {
        this.layoutParser = layoutParser;
        this.stateManager = stateManager;
        this.routeService = routeService;
    }

    /** FR-01/02/03/17/18: load (or reload) a legal XML layout; replaces any prior layout and state (SPR-06). */
    @PostMapping("/layout")
    public LoadLayoutResponse loadLayout(@RequestBody String xmlContent) {
        LayoutGraph graph = layoutParser.parse(new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8)));
        stateManager.loadLayout(graph);

        long pointCount = graph.allTrackSections().stream().filter(TrackSection::isPoint).count();
        return new LoadLayoutResponse(graph.allTrackSections().size(), (int) pointCount, graph.allMarkerboards().size());
    }

    /** FR-04: configure initial track occupancy on the loaded layout. */
    @PostMapping("/occupancy")
    public StateSnapshotResponse setOccupancy(@RequestBody SetOccupancyRequest request) {
        List<String> ids = request.trackSectionIds() == null ? List.of() : request.trackSectionIds();
        stateManager.setInitialOccupancy(ids);
        return snapshot();
    }

    /** FR-05..08: find a free path between origin and destination and reserve it. */
    @PostMapping("/route")
    public RouteResponse findRoute(@RequestBody FindRouteRequest request) {
        RouteResult result = routeService.findAndReserveRoute(request.originId(), request.destinationId());
        return new RouteResponse(result.path(), result.reservedTrackIds(), result.pointsUsed());
    }

    /** FR-09: release a previously reserved route (or any subset of it). */
    @PostMapping("/route/release")
    public StateSnapshotResponse releaseRoute(@RequestBody ReleaseRouteRequest request) {
        List<String> ids = request.trackSectionIds() == null ? List.of() : request.trackSectionIds();
        stateManager.releaseReservation(ids);
        return snapshot();
    }

    /** NFR-03: current occupancy/reservation snapshot for the dashboard. */
    @GetMapping("/state")
    public StateSnapshotResponse getState() {
        return snapshot();
    }

    /** FR-16/SPR-05: clear scenario occupancy/reservations, keep the loaded layout. */
    @PostMapping("/state/clear")
    public StateSnapshotResponse clearState() {
        stateManager.clearState();
        return snapshot();
    }

    private StateSnapshotResponse snapshot() {
        boolean loaded = stateManager.hasLayout();
        List<String> allTrackSectionIds = loaded
                ? stateManager.requireLayout().allTrackSections().stream().map(TrackSection::id).sorted().toList()
                : List.of();
        return new StateSnapshotResponse(
                loaded,
                allTrackSectionIds,
                stateManager.occupiedTracks().stream().sorted().toList(),
                stateManager.reservedTracks().stream().sorted().toList());
    }
}
