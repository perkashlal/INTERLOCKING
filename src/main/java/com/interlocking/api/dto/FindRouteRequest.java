package com.interlocking.api.dto;

/**
 * Request body for POST /api/route (FR-05). Origin/destination may each be a
 * track section id or a markerboard id, resolved against the loaded layout.
 */
public record FindRouteRequest(String originId, String destinationId) {
}
