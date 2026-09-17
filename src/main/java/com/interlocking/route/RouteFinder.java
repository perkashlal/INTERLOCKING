package com.interlocking.route;

import com.interlocking.model.LayoutGraph;
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
 * trackSection (SRS Table 6) is an ordinary graph node with three neighbors
 * (plus/minus/stem); a path can only enter and leave through two of them, so a
 * found route can never straddle both branches of a point at once.
 *
 * <p>The origin section is exempt from the free-section check passed in, since it
 * is expected to already be occupied by the train requesting the route; every
 * other section on the path — including the destination — must satisfy the
 * predicate (NFR-01: usable only if neither occupied nor already reserved) or the
 * route is rejected (FR-07).
 */
@Component
public class RouteFinder {

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
            for (String neighborId : layout.neighborsOf(current)) {
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
