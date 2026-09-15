package com.interlocking.model;

import java.util.List;

/**
 * A physical track resource (SRS Table 6). Purely structural — occupancy/reservation
 * status is tracked separately by ScenarioStateManager, since the same layout can be
 * reused across many scenario states. A trackSection with type POINT is a branching
 * resource (SRS "Point / switch"); it is still an ordinary node in the connectivity
 * graph, just one that route finding must also report as a reserved point (FR-08).
 */
public record TrackSection(String id, double length, TrackSectionType type, List<Neighbor> neighbors) {

    public List<String> neighborIds() {
        return neighbors.stream().map(Neighbor::ref).toList();
    }

    public boolean isPoint() {
        return type == TrackSectionType.POINT;
    }
}
