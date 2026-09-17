package com.interlocking.simulation;

import com.interlocking.state.ScenarioStateManager;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Moves a train section-by-section along an already-reserved route (FR-11/12/13,
 * AC-08). Each step frees the section the train just left and occupies the one it
 * has just entered, so that once the simulation completes only the destination is
 * occupied and every other section the route passed through - including the
 * origin - is free again (SPR-02).
 *
 * <p>Safety-first (NFR-06): every section of the path after the origin must
 * currently be reserved before the simulation is allowed to run, so a route can
 * never be "driven" unless it was actually granted by {@code RouteService}.
 */
@Component
public class MovementSimulator {

    private final ScenarioStateManager stateManager;

    public MovementSimulator(ScenarioStateManager stateManager) {
        this.stateManager = stateManager;
    }

    public synchronized void simulate(List<String> path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Path to simulate must not be empty");
        }
        for (int i = 1; i < path.size(); i++) {
            String sectionId = path.get(i);
            if (!stateManager.isReserved(sectionId)) {
                throw new IllegalStateException(
                        "Cannot simulate movement: '" + sectionId + "' is not currently reserved for this route");
            }
        }

        String previous = null;
        for (String sectionId : path) {
            if (previous != null) {
                stateManager.markFree(previous);
            }
            stateManager.markOccupied(sectionId);
            previous = sectionId;
        }
    }
}
