package com.interlocking.route;

import com.interlocking.exception.RouteNotFoundException;
import com.interlocking.model.LayoutGraph;
import com.interlocking.model.Markerboard;
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

class RouteReservationServiceTest {

    private final ScenarioStateManager stateManager = new ScenarioStateManager();
    private final RouteReservationService service = new RouteReservationService(new RouteFinder(), stateManager);

    @BeforeEach
    void loadLayout() {
        Map<String, TrackSection> sections = new LinkedHashMap<>();
        sections.put("T1", new TrackSection("T1", 100, TrackSectionType.LINEAR, List.of(new Neighbor("T2", "down"))));
        sections.put("T2", new TrackSection("T2", 100, TrackSectionType.LINEAR,
                List.of(new Neighbor("T1", "up"), new Neighbor("T3", "down"))));
        sections.put("T3", new TrackSection("T3", 100, TrackSectionType.LINEAR, List.of(new Neighbor("T2", "up"))));
        Map<String, Markerboard> markerboards = Map.of(
                "M1", new Markerboard("M1", "T1", "up", 20.0),
                "M2", new Markerboard("M2", "T3", "down", 20.0));
        stateManager.loadLayout(new LayoutGraph(sections, markerboards));
    }

    @Test
    void reservesEveryTrackSectionOnTheFoundRoute() {
        Route route = service.findAndReserveRoute("T1", "T3");

        assertThat(route.trackSectionIds()).containsExactly("T1", "T2", "T3");
        assertThat(stateManager.reservedTracks()).containsExactlyInAnyOrder("T1", "T2", "T3");
    }

    @Test
    void resolvesMarkerboardIdsToTrackSections() {
        Route route = service.findAndReserveRoute("M1", "M2");

        assertThat(route.trackSectionIds()).containsExactly("T1", "T2", "T3");
    }

    @Test
    void secondOverlappingRouteRequestIsRejectedUntilReleased() {
        service.findAndReserveRoute("T1", "T3");

        assertThatThrownBy(() -> service.findAndReserveRoute("T1", "T3"))
                .isInstanceOf(RouteNotFoundException.class);

        stateManager.clearState();
        assertThat(service.findAndReserveRoute("T1", "T3").trackSectionIds()).containsExactly("T1", "T2", "T3");
    }

    @Test
    void unknownEndpointIdIsRejected() {
        assertThatThrownBy(() -> service.findAndReserveRoute("T1", "GHOST"))
                .isInstanceOf(RouteNotFoundException.class);
    }
}
