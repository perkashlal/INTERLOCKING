package com.interlocking.route;

import com.interlocking.model.LayoutGraph;
import com.interlocking.model.Markerboard;
import com.interlocking.model.Neighbor;
import com.interlocking.model.TrackSection;
import com.interlocking.model.TrackSectionType;
import com.interlocking.parser.XmlLayoutParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RouteFinderTest {

    private final RouteFinder routeFinder = new RouteFinder();

    /** T1 - T2 - T3 - T4 - T5, all linear, matching sample-layouts/simple-line.xml. */
    private LayoutGraph linearLayout() {
        Map<String, TrackSection> sections = new LinkedHashMap<>();
        sections.put("T1", new TrackSection("T1", 100, TrackSectionType.LINEAR, List.of(new Neighbor("T2", "down"))));
        sections.put("T2", new TrackSection("T2", 100, TrackSectionType.LINEAR,
                List.of(new Neighbor("T1", "up"), new Neighbor("T3", "down"))));
        sections.put("T3", new TrackSection("T3", 100, TrackSectionType.LINEAR,
                List.of(new Neighbor("T2", "up"), new Neighbor("T4", "down"))));
        sections.put("T4", new TrackSection("T4", 100, TrackSectionType.LINEAR,
                List.of(new Neighbor("T3", "up"), new Neighbor("T5", "down"))));
        sections.put("T5", new TrackSection("T5", 100, TrackSectionType.LINEAR, List.of(new Neighbor("T4", "up"))));
        return new LayoutGraph(sections, Map.of());
    }

    /**
     * T1 - T2 -(P1)-+- T3 - T5
     *                +- T4 - T6
     * matching sample-layouts/junction-layout.xml.
     */
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
        return new LayoutGraph(sections,
                Map.of("M1", new Markerboard("M1", "T1", "up", 20.0), "M2", new Markerboard("M2", "T5", "down", 20.0)));
    }

    @Test
    void findsStraightPathAlongLinearTrack() {
        Optional<List<String>> path = routeFinder.findRoute(linearLayout(), "T1", "T5", id -> true);

        assertThat(path).contains(List.of("T1", "T2", "T3", "T4", "T5"));
    }

    @Test
    void findsPathThroughAPointToOneBranch() {
        Optional<List<String>> path = routeFinder.findRoute(junctionLayout(), "T1", "T5", id -> true);

        assertThat(path).contains(List.of("T1", "T2", "P1", "T3", "T5"));
    }

    @Test
    void doesNotRouteThroughTheOtherBranchOfThePoint() {
        Optional<List<String>> path = routeFinder.findRoute(junctionLayout(), "T1", "T5", id -> true);

        assertThat(path.orElseThrow()).doesNotContain("T4", "T6");
    }

    @Test
    void returnsEmptyWhenDestinationIsBlocked() {
        Set<String> blocked = Set.of("T5");
        Optional<List<String>> path = routeFinder.findRoute(linearLayout(), "T1", "T5", id -> !blocked.contains(id));

        assertThat(path).isEmpty();
    }

    @Test
    void returnsEmptyWhenAnIntermediateSectionIsBlocked() {
        Set<String> blocked = Set.of("T3");
        Optional<List<String>> path = routeFinder.findRoute(linearLayout(), "T1", "T5", id -> !blocked.contains(id));

        assertThat(path).isEmpty();
    }

    @Test
    void routesAroundABlockedBranchByTakingTheOtherOne() {
        Set<String> blocked = Set.of("T3");
        Optional<List<String>> path = routeFinder.findRoute(junctionLayout(), "T1", "T6", id -> !blocked.contains(id));

        assertThat(path).contains(List.of("T1", "T2", "P1", "T4", "T6"));
    }

    @Test
    void originDoesNotNeedToBeFreeItself() {
        Set<String> blocked = Set.of("T1");
        Optional<List<String>> path = routeFinder.findRoute(linearLayout(), "T1", "T3", id -> !blocked.contains(id));

        assertThat(path).contains(List.of("T1", "T2", "T3"));
    }

    @Test
    void returnsEmptyForUnknownTrackSections() {
        assertThat(routeFinder.findRoute(linearLayout(), "GHOST", "T1", id -> true)).isEmpty();
        assertThat(routeFinder.findRoute(linearLayout(), "T1", "GHOST", id -> true)).isEmpty();
    }

    @Test
    void singleSectionRouteWhenOriginEqualsDestination() {
        assertThat(routeFinder.findRoute(linearLayout(), "T1", "T1", id -> true)).contains(List.of("T1"));
    }

    @Test
    void neverStraddlesBothBranchesOfAPointInOneRoute() {
        // T3 and T4 are only connected to each other through P1 (plus and minus
        // respectively); a point can't connect its plus and minus legs directly
        // (only stem<->plus or stem<->minus), so no route between them can exist.
        Optional<List<String>> path = routeFinder.findRoute(junctionLayout(), "T3", "T4", id -> true);

        assertThat(path).isEmpty();
    }

    @Test
    void findsRouteThroughTwoPointsOnTheRealExportedLayout() throws IOException {
        LayoutGraph layout = new XmlLayoutParser().parse(sampleLayout("lvr_1.xml"));

        // 533 -(plus)- PM01U -(stem)- 083 -(stem)- PM02U -(plus)- PM03U: enters and
        // leaves each point via a stem<->plus pair, so it's a legal route.
        Optional<List<String>> path = routeFinder.findRoute(layout, "533", "PM03U", id -> true);

        assertThat(path).contains(List.of("533", "PM01U", "083", "PM02U", "PM03U"));
    }

    @Test
    void rejectsPlusToMinusStraddleOnTheRealExportedLayout() throws IOException {
        LayoutGraph layout = new XmlLayoutParser().parse(sampleLayout("lvr_1.xml"));

        // 533 is PM01U's plus leg and 534 is its minus leg; PM01U is their only
        // connection, so straddling it directly must be rejected.
        Optional<List<String>> path = routeFinder.findRoute(layout, "533", "534", id -> true);

        assertThat(path).isEmpty();
    }

    private InputStream sampleLayout(String fileName) throws IOException {
        return Files.newInputStream(Path.of("sample-layouts", fileName));
    }
}
