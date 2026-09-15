package com.interlocking.api.dto;

/** Response for POST /api/layout — summary of what was parsed and loaded (FR-01..03). */
public record LoadLayoutResponse(int trackSectionCount, int pointCount, int markerboardCount) {
}
