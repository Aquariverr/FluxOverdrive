package com.aquariverr.fluxod.miniature.compile;

import com.aquariverr.fluxod.miniature.model.*;
import java.util.*;
import static com.aquariverr.fluxod.miniature.compile.FactoryCompiler.rejected;

/** Shared directed routing rules for compilation and automatic balancing. */
final class FactoryTopology {
    static final class Node {
        final GridPosition position;
        final FactoryCell cell;
        final Map<Node, Integer> outgoing = new LinkedHashMap<>();
        final List<Node> incoming = new ArrayList<>();
        Node(GridPosition position, FactoryCell cell) { this.position = position; this.cell = cell; }
    }
    final Map<GridPosition, Node> nodes = new LinkedHashMap<>();
    final List<Node> ordered = new ArrayList<>();

    FactoryTopology(FactoryLayout layout) {
        if (layout.machineCount() == 0) throw rejected(FactoryDiagnostic.Code.NO_MACHINES, null, null, null, null);
        for (int y = 0; y < layout.size(); y++) {
            for (int x = 0; x < layout.size(); x++) {
                var position = new GridPosition(x, y);
                var cell = layout.cellAt(position);
                if (!(cell instanceof FactoryCell.Empty)) nodes.put(position, new Node(position, cell));
            }
            var output = new GridPosition(layout.size(), y);
            nodes.put(output, new Node(output, null));
        }
        for (Node node : nodes.values()) for (var branch : weights(node.cell).entrySet()) {
            GridDirection direction = branch.getKey();
            int x = node.position.x() + direction.deltaX(), y = node.position.y() + direction.deltaY();
            Node target = x < 0 || x > layout.size() || y < 0 || y >= layout.size() ? null : nodes.get(new GridPosition(x, y));
            if (target == null || weights(target.cell).keySet().stream().anyMatch(facing ->
                    target.position.x() + facing.deltaX() == node.position.x() && target.position.y() + facing.deltaY() == node.position.y())) {
                throw rejected(FactoryDiagnostic.Code.ROUTE_DEAD_END, node.position, null, null, null);
            }
            node.outgoing.put(target, branch.getValue());
            target.incoming.add(node);
        }
        Map<Node, Integer> degrees = new IdentityHashMap<>();
        var ready = new ArrayDeque<Node>();
        for (Node node : nodes.values()) {
            degrees.put(node, node.incoming.size());
            if (node.incoming.isEmpty()) ready.add(node);
        }
        while (!ready.isEmpty()) {
            Node node = ready.remove(); ordered.add(node);
            for (Node target : node.outgoing.keySet()) if (degrees.compute(target, (ignored, degree) -> degree - 1) == 0) ready.add(target);
        }
        if (ordered.size() != nodes.size()) {
            Node node = nodes.values().stream().filter(value -> degrees.get(value) > 0).findFirst().orElseThrow();
            Set<Node> visited = Collections.newSetFromMap(new IdentityHashMap<>());
            while (visited.add(node)) node = node.incoming.stream().filter(value -> degrees.get(value) > 0).findFirst().orElseThrow();
            throw rejected(FactoryDiagnostic.Code.ROUTE_CYCLE, node.position, null, null, null);
        }
    }

    private static Map<GridDirection, Integer> weights(FactoryCell cell) {
        if (cell instanceof FactoryCell.Arrow arrow) return arrow.weights();
        if (cell instanceof FactoryCell.Machine machine) {
            var directions = new EnumMap<GridDirection, Integer>(GridDirection.class);
            machine.directions().forEach(direction -> directions.put(direction, 1));
            return directions;
        }
        if (cell instanceof FactoryCell.Marker marker) return Map.of(marker.direction(), 1);
        return Map.of();
    }
}
