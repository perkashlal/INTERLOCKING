package com.interlocking.route;

import com.interlocking.model.LayoutGraph;
import com.interlocking.state.ScenarioStateManager;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Orchestrates route finding and reservation (SRS FR-05..09): resolves the
 * caller-supplied ids (track section or markerboard), finds a free path with
 * {@link RouteFinder}, and reserves it in one atomic step so two concurrent
 * requests can never both reserve the same track section (NFR-01).
 *
 * <p>Finding a route and reserving it are synchronized on the state manager
 * itself, the same monitor its own mutating methods use, so nothing can reserve
 * a section between this class reading it as free and it actually reserving it.
 */
@Component
public class RouteService {

    private final RouteFinder routeFinder;
    private final ScenarioStateManager stateManager;

    public RouteService(RouteFinder routeFinder, ScenarioStateManager stateManager) {
        this.routeFinder = routeFinder;
        this.stateManager = stateManager;
    }

    public RouteResult findAndReserveRoute(String originIdOrMarkerboard, String destinationIdOrMarkerboard) {
        synchronized (stateManager) {
            LayoutGraph layout = stateManager.requireLayout();
            String originId = resolve(layout, originIdOrMarkerboard, "origin");
            String destinationId = resolve(layout, destinationIdOrMarkerboard, "destination");
            if (originId.equals(destinationId)) {
                throw new IllegalArgumentException("Origin and destination must resolve to different track sections");
            }

            List<String> path = routeFinder.findRoute(layout, originId, destinationId, stateManager::isFree)
                    .orElseThrow(() -> new IllegalStateException(
                            "No available route from '" + originId + "' to '" + destinationId
                                    + "': blocked by occupied or already-reserved track sections"));

            List<String> reservedTrackIds = path.stream().filter(id -> !id.equals(originId)).toList();
            stateManager.reserve(reservedTrackIds);

            List<String> pointsUsed = path.stream().filter(layout::isPoint).toList();
            return new RouteResult(path, reservedTrackIds, pointsUsed);
        }
    }

    private String resolve(LayoutGraph layout, String idOrMarkerboard, String label) {
        if (idOrMarkerboard == null || idOrMarkerboard.isBlank()) {
            throw new IllegalArgumentException("Missing " + label + " id");
        }
        return layout.resolveTrackSectionId(idOrMarkerboard)
                .orElseThrow(() -> new IllegalArgumentException("Unknown " + label + " id: " + idOrMarkerboard));
    }
}
