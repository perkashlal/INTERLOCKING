package com.interlocking.model;

import java.util.List;

/**
 * A physical track resource (SRS Table 6). Purely structural — occupancy/reservation
 * status is tracked separately by ScenarioStateManager, since the same layout can be
 * reused across many scenario states.
 */
public record TrackSection(String id, List<String> neighborIds) {
}
