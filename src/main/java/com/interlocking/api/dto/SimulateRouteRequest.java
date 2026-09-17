package com.interlocking.api.dto;

import java.util.List;

/**
 * Request body for POST /api/route/simulate (FR-11): the full track section path
 * returned by a prior POST /api/route call, including its origin.
 */
public record SimulateRouteRequest(List<String> trackSectionPath) {
}
