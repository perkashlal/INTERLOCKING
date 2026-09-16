package com.interlocking.api.dto;

import java.util.List;

/**
 * Response for POST /api/route: the full path found, the sections newly reserved
 * for it, and which of those are points held by the route (FR-05..08).
 */
public record RouteResponse(List<String> trackSectionPath, List<String> reservedTrackIds, List<String> pointsUsed) {
}
