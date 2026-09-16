package com.interlocking.route;

import java.util.List;

/**
 * Outcome of a successful route reservation: the full path from origin to
 * destination, the subset of it newly reserved (path minus the already-occupied
 * origin), and which of those sections are points (SRS FR-08: report points held
 * by the route).
 */
public record RouteResult(List<String> path, List<String> reservedTrackIds, List<String> pointsUsed) {
}
