package com.interlocking.route;

import com.interlocking.exception.RouteNotFoundException;
import com.interlocking.model.LayoutGraph;
import com.interlocking.model.Neighbor;
import com.interlocking.model.TrackSection;
import com.interlocking.model.TrackSectionType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RouteFinderTest {

    private final RouteFinder routeFinder = new RouteFinder();

    /** T1 - T2 - P1 -+- T3 - T5   (P1 plus leg -> T3, minus leg -> T4) */
    private LayoutGraph junctionLayout() {
        Map<String, TrackSection> sections = new LinkedHashMap<>();
        sections.put("T1", new TrackSection("T1", 100, TrackSectionType.LINEAR, List.of(new Neighbor("T2", "down"))));
        sections.put("T2", new TrackSection("T2", 100, TrackSectionType.LINEAR,
                List.of(new Neighbor("T1", "up"), new Neighbor("P1", "down"))));
        sections.put("P1", new TrackSection("P1", 50, TrackSectionType.POINT,
                List.of(new Neighbor("T2", "stem"), new Neighbor("T3", "plus"), new Neighbor("T4", "minus"))));
        sections.put("T3", new TrackSection("T3", 100, TrackSectionType.LINEAR,
                List.of(new Neighbor("P1", "up"), new Neighbor("T5", "down"))));
        sections.put("T4", new TrackSection("T4", 100, TrackSectionType.LINEAR,
                List.of(new Neighbor("P1", "up"), new Neighbor("T6", "down"))));
        sections.put("T5", new TrackSection("T5", 100, TrackSectionType.LINEAR, List.of(new Neighbor("T3", "up"))));
        sections.put("T6", new TrackSection("T6", 100, TrackSectionType.LINEAR, List.of(new Neighbor("T4", "up"))));
        return new LayoutGraph(sections, Map.of());
    }

    @Test
    void findsSimpleLinearRoute() {
        Route route = routeFinder.findRoute(junctionLayout(), id -> true, "T1", "T2");

        assertThat(route.trackSectionIds()).containsExactly("T1", "T2");
        assertThat(route.pointPositions()).isEmpty();
    }

    @Test
    void sameSourceAndDestinationIsATrivialRoute() {
        Route route = routeFinder.findRoute(junctionLayout(), id -> true, "T1", "T1");

        assertThat(route.trackSectionIds()).containsExactly("T1");
        assertThat(route.pointPositions()).isEmpty();
    }

    @Test
    void routeThroughPlusLegSetsPointToPlus() {
        Route route = routeFinder.findRoute(junctionLayout(), id -> true, "T1", "T5");

        assertThat(route.trackSectionIds()).containsExactly("T1", "T2", "P1", "T3", "T5");
        assertThat(route.pointPositions()).containsExactly(Map.entry("P1", PointPosition.PLUS));
    }

    @Test
    void routeThroughMinusLegSetsPointToMinus() {
        Route route = routeFinder.findRoute(junctionLayout(), id -> true, "T1", "T6");

        assertThat(route.trackSectionIds()).containsExactly("T1", "T2", "P1", "T4", "T6");
        assertThat(route.pointPositions()).containsExactly(Map.entry("P1", PointPosition.MINUS));
    }

    @Test
    void cannotCrossDirectlyBetweenPlusAndMinusLegs() {
        // The only way from T3 to T4 would be straight across P1's plus and minus legs,
        // which isn't a legal point traversal, so no route exists even though a naive
        // graph search without the point-side rule would find one via P1.
        assertThatThrownBy(() -> routeFinder.findRoute(junctionLayout(), id -> true, "T3", "T4"))
                .isInstanceOf(RouteNotFoundException.class);
    }

    @Test
    void blockedIntermediateSectionMakesNoRouteAvailable() {
        Set<String> blocked = Set.of("P1");
        assertThatThrownBy(() -> routeFinder.findRoute(junctionLayout(), id -> !blocked.contains(id), "T1", "T5"))
                .isInstanceOf(RouteNotFoundException.class);
    }

    @Test
    void occupiedSourceIsRejected() {
        Set<String> blocked = Set.of("T1");
        assertThatThrownBy(() -> routeFinder.findRoute(junctionLayout(), id -> !blocked.contains(id), "T1", "T5"))
                .isInstanceOf(RouteNotFoundException.class);
    }

    @Test
    void unknownEndpointIsRejected() {
        assertThatThrownBy(() -> routeFinder.findRoute(junctionLayout(), id -> true, "T1", "GHOST"))
                .isInstanceOf(RouteNotFoundException.class);
    }

    @Test
    void disconnectedSectionsHaveNoRoute() {
        Map<String, TrackSection> sections = new LinkedHashMap<>();
        sections.put("A1", new TrackSection("A1", 100, TrackSectionType.LINEAR, List.of()));
        sections.put("B1", new TrackSection("B1", 100, TrackSectionType.LINEAR, List.of()));
        LayoutGraph layout = new LayoutGraph(sections, Map.of());

        assertThatThrownBy(() -> routeFinder.findRoute(layout, id -> true, "A1", "B1"))
                .isInstanceOf(RouteNotFoundException.class);
    }
}
