package com.interlocking.route;

import com.interlocking.exception.RouteNotFoundException;
import com.interlocking.model.LayoutGraph;
import com.interlocking.state.ScenarioStateManager;
import org.springframework.stereotype.Component;

/**
 * Finds a safe route and reserves it in one step (SRS Fig. 2 Reservation). Accepts
 * track section ids or markerboard ids for endpoints, same as the rest of the
 * dashboard API. The check-then-reserve sequence is synchronized on the shared
 * {@link ScenarioStateManager} so two concurrent requests can never both be granted
 * the same track section (NFR-01).
 */
@Component
public class RouteReservationService {

    private final RouteFinder routeFinder;
    private final ScenarioStateManager stateManager;

    public RouteReservationService(RouteFinder routeFinder, ScenarioStateManager stateManager) {
        this.routeFinder = routeFinder;
        this.stateManager = stateManager;
    }

    /** FR-05..FR-15/FR-08: find a safe route between two tracks and reserve every section on it. */
    public Route findAndReserveRoute(String fromIdOrMarkerboard, String toIdOrMarkerboard) {
        synchronized (stateManager) {
            LayoutGraph layout = stateManager.requireLayout();
            String fromId = resolve(layout, fromIdOrMarkerboard);
            String toId = resolve(layout, toIdOrMarkerboard);

            Route route = routeFinder.findRoute(layout, stateManager::isFree, fromId, toId);
            stateManager.reserve(route.trackSectionIds());
            return route;
        }
    }

    private String resolve(LayoutGraph layout, String idOrMarkerboard) {
        return layout.resolveTrackSectionId(idOrMarkerboard)
                .orElseThrow(() -> new RouteNotFoundException("Unknown track section or markerboard id: " + idOrMarkerboard));
    }
}
