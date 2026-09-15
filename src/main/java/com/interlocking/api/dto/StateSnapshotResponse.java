package com.interlocking.api.dto;

import java.util.List;

/**
 * Current occupancy/reservation snapshot for the dashboard (NFR-03), also returned
 * after every state-mutating call so the caller always sees the persisted result.
 */
public record StateSnapshotResponse(boolean layoutLoaded,
                                     List<String> allTrackSectionIds,
                                     List<String> occupiedTrackIds,
                                     List<String> reservedTrackIds) {
}
