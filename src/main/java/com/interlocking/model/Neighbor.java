package com.interlocking.model;

/**
 * A directed connectivity edge declared on a trackSection element, e.g.
 * {@code <neighbor ref="PM01U" side="up"/>}. "side" is topology metadata
 * (up/down for linear track, plus/minus/stem for a point) kept for display
 * and traceability; route finding only needs {@link #ref()}.
 */
public record Neighbor(String ref, String side) {
}
