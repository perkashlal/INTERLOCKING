package com.interlocking.api.dto;

/** Request body for POST /api/route — endpoints may be track section or markerboard ids (FR-05). */
public record FindRouteRequest(String fromId, String toId) {
}
