package com.interlocking.route;

import com.interlocking.model.LayoutGraph;
import com.interlocking.parser.XmlLayoutParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Day 10: route finding against the real exported layout (lvr_1.xml), not just toy samples. */
class RealLayoutRouteTest {

    private final RouteFinder finder = new RouteFinder();

    private LayoutGraph load() throws Exception {
        try (InputStream in = Files.newInputStream(Path.of("sample-layouts", "lvr_1.xml"))) {
            return new XmlLayoutParser().parse(in);
        }
    }

    @Test
    void routesAreContiguousAndStartAndEndAtRequestedSections() throws Exception {
        LayoutGraph graph = load();
        List<String> path = finder.findRoute(graph, "533", "534", id -> true).orElseThrow();

        assertThat(path.get(0)).isEqualTo("533");
        assertThat(path.get(path.size() - 1)).isEqualTo("534");
        for (int i = 0; i < path.size() - 1; i++) {
            assertThat(graph.neighborsOf(path.get(i))).contains(path.get(i + 1));
        }
    }

    @Test
    void blockedDestinationIsRejectedOnRealLayout() throws Exception {
        Optional<List<String>> route = finder.findRoute(load(), "533", "534", id -> !id.equals("534"));
        assertThat(route).isEmpty();
    }
}
