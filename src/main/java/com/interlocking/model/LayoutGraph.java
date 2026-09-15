package com.interlocking.model;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The topology built from a loaded XML layout (SRS Section 6 / Fig. 2 LayoutGraph).
 * Immutable once built by XmlLayoutParser; scenario state (occupancy/reservation)
 * lives separately in ScenarioStateManager so a new scenario can reuse the same layout.
 */
public class LayoutGraph {

    private final Map<String, TrackSection> trackSections;
    private final Map<String, Markerboard> markerboards;

    public LayoutGraph(Map<String, TrackSection> trackSections, Map<String, Markerboard> markerboards) {
        this.trackSections = new LinkedHashMap<>(trackSections);
        this.markerboards = new LinkedHashMap<>(markerboards);
    }

    public Optional<TrackSection> trackSection(String id) {
        return Optional.ofNullable(trackSections.get(id));
    }

    public boolean hasTrackSection(String id) {
        return trackSections.containsKey(id);
    }

    public List<String> neighborsOf(String trackSectionId) {
        TrackSection section = trackSections.get(trackSectionId);
        return section == null ? List.of() : section.neighborIds();
    }

    public boolean isPoint(String trackSectionId) {
        TrackSection section = trackSections.get(trackSectionId);
        return section != null && section.isPoint();
    }

    /**
     * Resolves a user-supplied id to a track section id: accepted directly if it is
     * already a track section id, otherwise resolved via a markerboard mapping.
     */
    public Optional<String> resolveTrackSectionId(String idOrMarkerboardId) {
        if (trackSections.containsKey(idOrMarkerboardId)) {
            return Optional.of(idOrMarkerboardId);
        }
        Markerboard markerboard = markerboards.get(idOrMarkerboardId);
        return markerboard == null ? Optional.empty() : Optional.of(markerboard.trackSectionId());
    }

    public Collection<TrackSection> allTrackSections() {
        return trackSections.values();
    }

    public Collection<Markerboard> allMarkerboards() {
        return markerboards.values();
    }
}
