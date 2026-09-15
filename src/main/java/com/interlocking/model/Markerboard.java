package com.interlocking.model;

/**
 * A layout element that identifies route boundaries by mapping to a track section
 * (SRS Table 6), e.g. {@code <markerboard distance="20.0" id="LU11" mounted="up" track="533"/>}.
 * Lets the dashboard address tracks by markerboard id as well as by track section id.
 */
public record Markerboard(String id, String trackSectionId, String mounted, double distance) {
}
