package com.aquariverr.fluxod.miniature.model;

import java.util.Objects;

public sealed interface FactoryCell {

    enum Empty implements FactoryCell {
        INSTANCE
    }

    record Machine(MachineStack stack, RecipeReference recipe, GridDirection direction,
                   java.util.Map<ResourceKey, GridDirection> outputDirections) implements FactoryCell {
        public Machine {
            Objects.requireNonNull(stack, "stack");
            Objects.requireNonNull(direction, "direction");
            outputDirections = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(outputDirections));
            if (outputDirections.size() > FactoryLimits.MAX_RECIPE_RESOURCES || !outputDirections.isEmpty() && recipe == null)
                throw new IllegalArgumentException("Product routing requires a bounded selected recipe");
            outputDirections.forEach((resource, facing) -> { Objects.requireNonNull(resource); Objects.requireNonNull(facing); });
        }

        public Machine(MachineStack stack, RecipeReference recipe, GridDirection direction) {
            this(stack, recipe, direction, java.util.Map.of());
        }

        public GridDirection outputDirection(ResourceKey resource) { return outputDirections.getOrDefault(resource, direction); }
        public java.util.Set<GridDirection> directions() {
            return outputDirections.isEmpty() ? java.util.Set.of(direction) : java.util.Set.copyOf(outputDirections.values());
        }
        public boolean routesMatch(java.util.Set<ResourceKey> outputs) {
            return outputDirections.isEmpty() || outputDirections.keySet().equals(outputs);
        }
        public Machine facing(GridDirection facing) {
            int turns = Math.floorMod(facing.ordinal() - direction.ordinal(), GridDirection.values().length);
            var changed = new java.util.LinkedHashMap<ResourceKey, GridDirection>();
            outputDirections.forEach((key, value) -> changed.put(key, GridDirection.values()[(value.ordinal() + turns) % GridDirection.values().length]));
            return new Machine(stack, recipe, facing, changed);
        }

        public Machine(MachineStack stack, RecipeReference recipe) {
            this(stack, recipe, GridDirection.RIGHT);
        }

        public Machine(MachineStack stack) {
            this(stack, null);
        }
    }

    record Marker(ResourceAmount resource, GridDirection direction) implements FactoryCell {
        public Marker {
            Objects.requireNonNull(resource, "resource");
            Objects.requireNonNull(direction, "direction");
        }

        public Marker(ResourceKey resource) {
            this(ResourceAmount.defaultMarker(resource), GridDirection.RIGHT);
        }
    }

    record Arrow(java.util.Map<GridDirection, Integer> weights) implements FactoryCell {
        public static final int MAX_WEIGHT = 1_000_000;
        public Arrow {
            var copy = new java.util.EnumMap<GridDirection, Integer>(GridDirection.class);
            weights.forEach((direction, weight) -> {
                Objects.requireNonNull(direction);
                if (weight == null || weight < 1 || weight > MAX_WEIGHT) throw new IllegalArgumentException("Invalid arrow weight");
                copy.put(direction, weight);
            });
            if (copy.isEmpty()) throw new IllegalArgumentException("An arrow needs an output");
            weights = java.util.Collections.unmodifiableMap(copy);
        }

        public Arrow(GridDirection direction) { this(java.util.Map.of(direction, 1)); }
        public Arrow() {
            this(GridDirection.RIGHT);
        }
        public GridDirection direction() { return weights.keySet().iterator().next(); }
        public int weight(GridDirection direction) { return weights.getOrDefault(direction, 0); }
        public Arrow withWeight(GridDirection direction, int weight) {
            var changed = new java.util.EnumMap<GridDirection, Integer>(GridDirection.class);
            changed.putAll(weights);
            if (weight == 0) changed.remove(direction); else changed.put(direction, weight);
            return new Arrow(changed);
        }
        public Arrow rotated() {
            var changed = new java.util.EnumMap<GridDirection, Integer>(GridDirection.class);
            weights.forEach((direction, weight) -> changed.put(direction.clockwise(), weight));
            return new Arrow(changed);
        }
    }
}
