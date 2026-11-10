package com.aquariverr.fluxod.miniature.recipe;

import com.aquariverr.fluxod.miniature.model.*;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** Resource identities reachable upstream while editing; independent of amounts and unrelated dead ends. */
final class RoutedRecipeInputs {
    private final FactoryLayout layout;
    private final MiniatureRecipeCatalog catalog;
    private final ServerLevel level;
    private final Map<GridPosition, Map<GridDirection, List<ResourceKey>>> outputs = new HashMap<>();
    private final Set<GridPosition> visiting = new HashSet<>();

    RoutedRecipeInputs(FactoryLayout layout, MiniatureRecipeCatalog catalog, ServerLevel level) {
        this.layout = layout; this.catalog = catalog; this.level = level;
    }

    List<ResourceKey> inputs(GridPosition position) {
        var incoming = new LinkedHashSet<ResourceKey>();
        Set<GridDirection> exits = directions(layout.cellAt(position));
        for (var side : GridDirection.values()) {
            if (exits.contains(side)) continue; // An output face cannot also receive input.
            int x = position.x() + side.deltaX(), y = position.y() + side.deltaY();
            if (x < 0 || y < 0 || x >= layout.size() || y >= layout.size()) continue;
            var towards = side.clockwise().clockwise();
            var source = new GridPosition(x, y);
            if (!directions(layout.cellAt(source)).contains(towards)) continue;
            incoming.addAll(outputs(source).getOrDefault(towards, List.of()));
            if (incoming.size() > FactoryLimits.MAX_BOUNDARY_RESOURCES) throw new MiniatureRecipeCatalog.SearchLimit();
        }
        return List.copyOf(incoming);
    }

    private Map<GridDirection, List<ResourceKey>> outputs(GridPosition position) {
        var cached = outputs.get(position);
        if (cached != null) return cached;
        if (!visiting.add(position)) return Map.of();
        try {
            var cell = layout.cellAt(position);
            Map<GridDirection, LinkedHashSet<ResourceKey>> routed = new EnumMap<>(GridDirection.class);
            if (cell instanceof FactoryCell.Marker marker) {
                routed.computeIfAbsent(marker.direction(), ignored -> new LinkedHashSet<>()).add(marker.resource().resource());
            } else if (cell instanceof FactoryCell.Arrow arrow) {
                var incoming = inputs(position);
                arrow.weights().keySet().forEach(direction -> routed.put(direction, new LinkedHashSet<>(incoming)));
            } else if (cell instanceof FactoryCell.Machine machine) {
                List<ResolvedRecipe> candidates;
                if (machine.recipe() != null) {
                    try { candidates = List.of(catalog.resolve(machine.stack().variant(), machine.recipe(), level)); }
                    catch (IllegalArgumentException invalid) { candidates = List.of(); }
                } else candidates = catalog.search(machine.stack().variant(), inputs(position), level).candidates();
                for (var recipe : candidates) for (var output : recipe.outputs()) {
                    var resources = routed.computeIfAbsent(machine.outputDirection(output.resource()), ignored -> new LinkedHashSet<>());
                    resources.add(output.resource());
                    if (resources.size() > FactoryLimits.MAX_BOUNDARY_RESOURCES) throw new MiniatureRecipeCatalog.SearchLimit();
                }
            }
            var result = new EnumMap<GridDirection, List<ResourceKey>>(GridDirection.class);
            routed.forEach((direction, resources) -> result.put(direction, List.copyOf(resources)));
            outputs.put(position, result);
            return result;
        } finally { visiting.remove(position); }
    }

    private static Set<GridDirection> directions(FactoryCell cell) {
        return switch (cell) {
            case FactoryCell.Machine machine -> machine.directions();
            case FactoryCell.Marker marker -> Set.of(marker.direction());
            case FactoryCell.Arrow arrow -> arrow.weights().keySet();
            case FactoryCell.Empty ignored -> Set.of();
        };
    }
}
