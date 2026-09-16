package com.interlocking.api.dto;

import java.util.List;

/** Request body for POST /api/route/release (FR-09): sections to un-reserve. */
public record ReleaseRouteRequest(List<String> trackSectionIds) {
}
