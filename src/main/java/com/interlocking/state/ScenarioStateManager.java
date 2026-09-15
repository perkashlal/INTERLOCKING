package com.interlocking.state;

import com.interlocking.model.LayoutGraph;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Stores the current layout and its occupancy/reservation state (SRS Fig. 2
 * ScenarioStateManager). This prototype assumes a single scenario user (Table 8), so
 * a single shared instance with synchronized access is sufficient rather than
 * per-session state.
 *
 * <p>State persistence rules (SRS Section 7): occupancy/reservation stays available
 * across requests until explicitly cleared (SPR-01/04), clearing keeps the layout
 * (SPR-05), and loading a new layout replaces both the layout and the state (SPR-06).
 */
@Component
public class ScenarioStateManager {

    private LayoutGraph currentLayout;
    private final Set<String> occupiedTracks = new LinkedHashSet<>();
    private final Set<String> reservedTracks = new LinkedHashSet<>();

    public synchronized void loadLayout(LayoutGraph layout) {
        this.currentLayout = layout;
        occupiedTracks.clear();
        reservedTracks.clear();
    }

    public synchronized boolean hasLayout() {
        return currentLayout != null;
    }

    public synchronized LayoutGraph requireLayout() {
        if (currentLayout == null) {
            throw new IllegalStateException("No layout is loaded yet");
        }
        return currentLayout;
    }

    /** FR-04: replaces the occupancy set on the currently loaded layout. */
    public synchronized void setInitialOccupancy(Collection<String> trackSectionIds) {
        LayoutGraph layout = requireLayout();
        for (String id : trackSectionIds) {
            if (!layout.hasTrackSection(id)) {
                throw new IllegalArgumentException("Unknown track section id: " + id);
            }
        }
        occupiedTracks.clear();
        occupiedTracks.addAll(trackSectionIds);
        reservedTracks.clear();
    }

    /** SPR-05: clears scenario occupancy/reservations but keeps the loaded layout. */
    public synchronized void clearState() {
        occupiedTracks.clear();
        reservedTracks.clear();
    }

    public synchronized Set<String> occupiedTracks() {
        return Set.copyOf(occupiedTracks);
    }

    public synchronized Set<String> reservedTracks() {
        return Set.copyOf(reservedTracks);
    }

    public synchronized boolean isOccupied(String trackSectionId) {
        return occupiedTracks.contains(trackSectionId);
    }

    public synchronized boolean isReserved(String trackSectionId) {
        return reservedTracks.contains(trackSectionId);
    }

    /** NFR-01: a section is usable by a new route only if it is neither occupied nor reserved. */
    public synchronized boolean isFree(String trackSectionId) {
        return !isOccupied(trackSectionId) && !isReserved(trackSectionId);
    }

    public synchronized void reserve(Collection<String> trackSectionIds) {
        reservedTracks.addAll(trackSectionIds);
    }

    public synchronized void releaseReservation(Collection<String> trackSectionIds) {
        reservedTracks.removeAll(trackSectionIds);
    }

    public synchronized void markOccupied(String trackSectionId) {
        occupiedTracks.add(trackSectionId);
        reservedTracks.remove(trackSectionId);
    }

    public synchronized void markFree(String trackSectionId) {
        occupiedTracks.remove(trackSectionId);
        reservedTracks.remove(trackSectionId);
    }
}
