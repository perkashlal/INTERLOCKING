package com.interlocking.route;

import com.interlocking.model.LayoutGraph;
import com.interlocking.model.Neighbor;
import com.interlocking.model.TrackSection;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Finds the shortest available path between two track sections in a LayoutGraph
 * (SRS FR-05/06: propose a route from an origin to a destination). A "point"
 * trackSection (SRS Table 6) is a graph node with three neighbors, one per side
 * (plus/minus/stem): mechanically, a point only ever connects its stem to one of
 * plus/minus at a time, never plus directly to minus, so a route that enters a
 * point via one side may only leave it via the opposite kind of side — a route
 * can never straddle both branches of a point at once (FR-08/NFR-06). Each
 * section, point or otherwise, is still visited at most once per route, matching
 * the fact that a physical resource can only be part of one route.
 *
 * <p>The origin section is exempt from the free-section check passed in, since it
 * is expected to already be occupied by the train requesting the route; every
 * other section on the path — including the destination — must satisfy the
 * predicate (NFR-01: usable only if neither occupied nor already reserved) or the
 * route is rejected (FR-07).
 */
@Component
public class RouteFinder {

    private static final String STEM = "stem";

    public Optional<List<String>> findRoute(LayoutGraph layout, String originId, String destinationId,
                                             Predicate<String> isFree) {
        if (!layout.hasTrackSection(originId) || !layout.hasTrackSection(destinationId)) {
            return Optional.empty();
        }
        if (originId.equals(destinationId)) {
            return Optional.of(List.of(originId));
        }

        Map<String, Double> distance = new HashMap<>();
        Map<String, String> previous = new HashMap<>();
        Set<String> visited = new HashSet<>();
        distance.put(originId, 0.0);

        PriorityQueue<String> queue = new PriorityQueue<>(
                Comparator.comparingDouble(id -> distance.getOrDefault(id, Double.MAX_VALUE)));
        queue.add(originId);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (!visited.add(current)) {
                continue;
            }
            if (current.equals(destinationId)) {
                break;
            }

            double currentDistance = distance.get(current);
            String arrivalSide = arrivalSideAt(layout, previous.get(current), current);
            for (String neighborId : allowedNextSections(layout, current, arrivalSide)) {
                if (visited.contains(neighborId) || !isFree.test(neighborId)) {
                    continue;
                }
                TrackSection neighborSection = layout.trackSection(neighborId).orElse(null);
                if (neighborSection == null) {
                    continue;
                }
                double candidateDistance = currentDistance + neighborSection.length();
                if (candidateDistance < distance.getOrDefault(neighborId, Double.MAX_VALUE)) {
                    distance.put(neighborId, candidateDistance);
                    previous.put(neighborId, current);
                    queue.add(neighborId);
                }
            }
        }

        if (!previous.containsKey(destinationId)) {
            return Optional.empty();
        }
        return Optional.of(reconstructPath(previous, originId, destinationId));
    }

    /** The side of {@code currentId} that connects to {@code predecessorId}, or null if there is none. */
    private String arrivalSideAt(LayoutGraph layout, String predecessorId, String currentId) {
        if (predecessorId == null) {
            return null;
        }
        return layout.trackSection(currentId).map(section -> sideOfNeighbor(section, predecessorId)).orElse(null);
    }

    /**
     * Sections {@code currentId} may legally continue onto. A non-point section, or a
     * point that is itself the route's origin (no arrival side to honor yet), has no
     * restriction. A point reached via its stem may only continue onto a plus/minus
     * neighbor, and vice versa — never stem-to-stem or plus-to-minus. A point whose
     * sides don't follow the stem/plus/minus convention (no side literally named
     * "stem") is left unrestricted rather than guessing at an unfamiliar layout.
     */
    private List<String> allowedNextSections(LayoutGraph layout, String currentId, String arrivalSide) {
        TrackSection section = layout.trackSection(currentId).orElse(null);
        if (section == null) {
            return List.of();
        }
        boolean hasStemPort = section.neighbors().stream().anyMatch(n -> STEM.equalsIgnoreCase(n.side()));
        if (!section.isPoint() || arrivalSide == null || !hasStemPort) {
            return section.neighborIds();
        }
        boolean arrivedViaStem = STEM.equalsIgnoreCase(arrivalSide);
        return section.neighbors().stream()
                .filter(neighbor -> STEM.equalsIgnoreCase(neighbor.side()) != arrivedViaStem)
                .map(Neighbor::ref)
                .toList();
    }

    private String sideOfNeighbor(TrackSection section, String neighborId) {
        return section.neighbors().stream()
                .filter(neighbor -> neighbor.ref().equals(neighborId))
                .map(Neighbor::side)
                .findFirst()
                .orElse(null);
    }

    private List<String> reconstructPath(Map<String, String> previous, String originId, String destinationId) {
        List<String> path = new ArrayList<>();
        String step = destinationId;
        path.add(step);
        while (!step.equals(originId)) {
            step = previous.get(step);
            path.add(step);
        }
        Collections.reverse(path);
        return path;
    }
}
