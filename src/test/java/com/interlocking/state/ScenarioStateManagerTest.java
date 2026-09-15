package com.interlocking.state;

import com.interlocking.model.LayoutGraph;
import com.interlocking.model.Markerboard;
import com.interlocking.model.Neighbor;
import com.interlocking.model.TrackSection;
import com.interlocking.model.TrackSectionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScenarioStateManagerTest {

    private final ScenarioStateManager stateManager = new ScenarioStateManager();

    @BeforeEach
    void loadTinyLayout() {
        Map<String, TrackSection> sections = new LinkedHashMap<>();
        sections.put("T1", new TrackSection("T1", 100, TrackSectionType.LINEAR, List.of(new Neighbor("T2", "down"))));
        sections.put("T2", new TrackSection("T2", 100, TrackSectionType.LINEAR, List.of(new Neighbor("T1", "up"))));
        stateManager.loadLayout(new LayoutGraph(sections, Map.of("M1", new Markerboard("M1", "T1", "up", 20.0))));
    }

    @Test
    void requireLayoutFailsWhenNoneLoaded() {
        ScenarioStateManager empty = new ScenarioStateManager();
        assertThatThrownBy(empty::requireLayout).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void setInitialOccupancyRejectsUnknownTrackSection() {
        assertThatThrownBy(() -> stateManager.setInitialOccupancy(List.of("GHOST")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void setInitialOccupancyMarksSectionsOccupied() {
        stateManager.setInitialOccupancy(List.of("T1"));

        assertThat(stateManager.isOccupied("T1")).isTrue();
        assertThat(stateManager.isFree("T1")).isFalse();
        assertThat(stateManager.isFree("T2")).isTrue();
    }

    @Test
    void clearStateKeepsLayoutButResetsOccupancy() {
        stateManager.setInitialOccupancy(List.of("T1"));
        stateManager.reserve(List.of("T2"));

        stateManager.clearState();

        assertThat(stateManager.occupiedTracks()).isEmpty();
        assertThat(stateManager.reservedTracks()).isEmpty();
        assertThat(stateManager.hasLayout()).isTrue();
    }

    @Test
    void loadLayoutReplacesLayoutAndResetsState() {
        stateManager.setInitialOccupancy(List.of("T1"));

        Map<String, TrackSection> newSections = new LinkedHashMap<>();
        newSections.put("X1", new TrackSection("X1", 50, TrackSectionType.LINEAR, List.of()));
        stateManager.loadLayout(new LayoutGraph(newSections, Map.of()));

        assertThat(stateManager.occupiedTracks()).isEmpty();
        assertThat(stateManager.requireLayout().hasTrackSection("T1")).isFalse();
        assertThat(stateManager.requireLayout().hasTrackSection("X1")).isTrue();
    }

    @Test
    void markOccupiedRemovesFromReservedAndMarkFreeClearsBoth() {
        stateManager.reserve(List.of("T2"));
        stateManager.markOccupied("T2");
        assertThat(stateManager.isReserved("T2")).isFalse();
        assertThat(stateManager.isOccupied("T2")).isTrue();

        stateManager.markFree("T2");
        assertThat(stateManager.isOccupied("T2")).isFalse();
        assertThat(stateManager.isReserved("T2")).isFalse();
    }
}
