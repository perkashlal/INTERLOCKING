package com.interlocking.api.dto;

import java.util.List;

/** Request body for POST /api/occupancy (FR-04). */
public record SetOccupancyRequest(List<String> trackSectionIds) {
}
