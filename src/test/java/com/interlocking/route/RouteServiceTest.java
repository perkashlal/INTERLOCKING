package com.interlocking.route;

import com.interlocking.model.LayoutGraph;
import com.interlocking.model.Neighbor;
import com.interlocking.model.TrackSection;
import com.interlocking.model.TrackSectionType;
import com.interlocking.state.ScenarioStateManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RouteServiceTest {

    private final ScenarioStateManager stateManager = new ScenarioStateManager();
    private final RouteService routeService = new RouteService(new RouteFinder(), stateManager);

    @BeforeEach
    void loadLinearLayout() {
        Map<String, TrackSection> sections = new LinkedHashMap<>();
        sections.put("T1", new TrackSection("T1", 100, TrackSectionType.LINEAR, List.of(new Neighbor("T2", "down"))));
        sections.put("T2", new TrackSection("T2", 100, TrackSectionType.LINEAR,
                List.of(new Neighbor("T1", "up"), new Neighbor("T3", "down"))));
        sections.put("T3", new TrackSection("T3", 100, TrackSectionType.LINEAR, List.of(new Neighbor("T2", "up"))));
        stateManager.loadLayout(new LayoutGraph(sections, Map.of()));
        stateManager.setInitialOccupancy(List.of("T1"));
    }

    @Test
    void findsAndReservesRouteExcludingTheOccupiedOrigin() {
        RouteResult result = routeService.findAndReserveRoute("T1", "T3");

        assertThat(result.path()).containsExactly("T1", "T2", "T3");
        assertThat(result.reservedTrackIds()).containsExactly("T2", "T3");
        assertThat(stateManager.isReserved("T2")).isTrue();
        assertThat(stateManager.isReserved("T3")).isTrue();
        assertThat(stateManager.isOccupied("T1")).isTrue();
    }

    @Test
    void secondRouteCannotReuseAnAlreadyReservedSection() {
        routeService.findAndReserveRoute("T1", "T3");

        assertThatThrownBy(() -> routeService.findAndReserveRoute("T3", "T1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No available route");
    }

    @Test
    void rejectsUnknownOriginOrDestination() {
        assertThatThrownBy(() -> routeService.findAndReserveRoute("GHOST", "T3"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> routeService.findAndReserveRoute("T1", "GHOST"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsSameOriginAndDestination() {
        assertThatThrownBy(() -> routeService.findAndReserveRoute("T1", "T1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresLayoutToBeLoaded() {
        RouteService fresh = new RouteService(new RouteFinder(), new ScenarioStateManager());

        assertThatThrownBy(() -> fresh.findAndReserveRoute("T1", "T2"))
                .isInstanceOf(IllegalStateException.class);
    }
}
