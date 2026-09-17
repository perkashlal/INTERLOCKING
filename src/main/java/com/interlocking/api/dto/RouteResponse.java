package com.interlocking.api.dto;

import com.interlocking.route.PointPosition;

import java.util.List;
import java.util.Map;

/** Response for POST /api/route: the reserved path and the throw position every point on it needs (FR-08). */
public record RouteResponse(List<String> trackSectionIds, Map<String, PointPosition> pointPositions) {
}
