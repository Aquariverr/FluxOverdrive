package com.aquariverr.fluxod.miniature.compile;

import com.aquariverr.fluxod.miniature.model.*;
import com.aquariverr.fluxod.miniature.recipe.ResolvedRecipe;

import java.math.BigInteger;
import java.util.*;

public final class FactoryCompiler {
    @FunctionalInterface
    public interface RecipeResolver {
        ResolvedRecipe resolve(GridPosition position, FactoryCell.Machine machine, List<ResourceAmount> incoming);
    }

    private FactoryCompiler() {}

    public static CompilationResult compile(FactoryLayout layout, long generation, RecipeResolver resolver) {
        if (generation < 1) throw new IllegalArgumentException("Invalid recipe generation");
        Objects.requireNonNull(resolver);
        return new Pass(layout, generation, resolver).run();
    }

    public static ModelValidationException rejected(FactoryDiagnostic.Code code, GridPosition position,
                                                     ResourceKey resource, BigInteger expected, BigInteger actual) {
        return new ModelValidationException(new FactoryDiagnostic(code, Optional.ofNullable(position),
                Optional.ofNullable(resource), Optional.ofNullable(expected), Optional.ofNullable(actual)), code.name());
    }

    private static final class Node {
        final GridPosition position;
        final FactoryCell cell;
        final List<Node> outgoing = new ArrayList<>();
        final List<Node> incoming = new ArrayList<>();
        final Map<ResourceKey, BigInteger> resources = new LinkedHashMap<>();
        final Map<ResourceKey, BigInteger> unprocessed = new LinkedHashMap<>();
        Node(GridPosition position, FactoryCell cell) { this.position = position; this.cell = cell; }
    }

    private static final class Pass {
        final FactoryLayout layout;
        final long generation;
        final RecipeResolver resolver;
        final Map<GridPosition, Node> nodes = new LinkedHashMap<>();
        final Map<ResourceKey, BigInteger> inputs = new LinkedHashMap<>();
        final Map<ResourceKey, BigInteger> outputs = new LinkedHashMap<>();
        final Map<Integer, List<ResourceAmount>> rows = new LinkedHashMap<>();
        final Map<GridPosition, ResolvedRecipe> recipes = new LinkedHashMap<>();
        final Map<GridPosition, BigInteger> operations = new LinkedHashMap<>();
        GridPosition current;

        Pass(FactoryLayout layout, long generation, RecipeResolver resolver) {
            this.layout = Objects.requireNonNull(layout);
            this.generation = generation;
            this.resolver = resolver;
        }

        CompilationResult run() {
            try {
                if (layout.machineCount() == 0) throw rejected(FactoryDiagnostic.Code.NO_MACHINES, null, null, null, null);
                buildGraph();
                List<Node> ordered = order();
                for (Node node : ordered) {
                    current = node.position;
                    process(node);
                }
                if (outputs.isEmpty()) throw rejected(FactoryDiagnostic.Code.EMPTY_OUTPUTS, null, null, null, null);
                Set<ResourceKey> boundary = new HashSet<>(inputs.keySet());
                boundary.addAll(outputs.keySet());
                if (boundary.size() > FactoryLimits.MAX_BOUNDARY_RESOURCES) {
                    throw rejected(FactoryDiagnostic.Code.RESOURCE_LIMIT_EXCEEDED, current, null,
                            BigInteger.valueOf(FactoryLimits.MAX_BOUNDARY_RESOURCES), BigInteger.valueOf(boundary.size()));
                }
                Map<ResourceKey, BigInteger> catalysts = reserveReturningCatalysts();
                if (outputs.isEmpty()) throw rejected(FactoryDiagnostic.Code.EMPTY_OUTPUTS, null, null, null, null);
                var compiled = new CompiledFactory(layout, FactoryFingerprint.of(layout, recipes), generation,
                        amounts(inputs), amounts(outputs), amounts(catalysts), rows, recipes, operations);
                compiled.requiredTicks(1, 1);
                return CompilationResult.success(compiled);
            } catch (ModelValidationException exception) {
                FactoryDiagnostic diagnostic = exception.diagnostic();
                if (diagnostic.position().isEmpty() && current != null) diagnostic = new FactoryDiagnostic(diagnostic.code(),
                        Optional.of(current), diagnostic.resource(), diagnostic.expected(), diagnostic.actual());
                return CompilationResult.failure(diagnostic);
            }
        }

        /** Only unchanged catalysts which cross both external boundaries can be reserved and reused. */
        Map<ResourceKey, BigInteger> reserveReturningCatalysts() {
            Map<ResourceKey, BigInteger> declared = new LinkedHashMap<>();
            recipes.forEach((position, recipe) -> merge(declared, map(recipe.catalystsForOperations(operations.get(position))), true));
            Map<ResourceKey, BigInteger> reserved = new LinkedHashMap<>();
            for (var catalyst : declared.entrySet()) {
                var key = catalyst.getKey();
                BigInteger amount = catalyst.getValue().min(inputs.getOrDefault(key, BigInteger.ZERO))
                        .min(outputs.getOrDefault(key, BigInteger.ZERO));
                if (amount.signum() == 0) continue;
                reserved.put(key, amount);
                subtract(inputs, key, amount);
                subtract(outputs, key, amount);
                BigInteger remaining = amount;
                for (var row : rows.entrySet()) {
                    var resources = map(row.getValue());
                    BigInteger returned = remaining.min(resources.getOrDefault(key, BigInteger.ZERO));
                    if (returned.signum() > 0) {
                        subtract(resources, key, returned);
                        row.setValue(amounts(resources));
                        remaining = remaining.subtract(returned);
                    }
                }
            }
            rows.entrySet().removeIf(row -> row.getValue().isEmpty());
            return reserved;
        }

        private static void subtract(Map<ResourceKey, BigInteger> amounts, ResourceKey key, BigInteger amount) {
            BigInteger remaining = amounts.get(key).subtract(amount);
            if (remaining.signum() == 0) amounts.remove(key);
            else amounts.put(key, remaining);
        }

        void buildGraph() {
            var topology = new FactoryTopology(layout);
            topology.nodes.forEach((position, node) -> nodes.put(position, new Node(position, node.cell)));
            for (var node : topology.nodes.values()) for (var target : node.outgoing.keySet()) {
                connect(nodes.get(node.position), nodes.get(target.position));
            }
        }

        Node neighbor(Node node, GridDirection direction) {
            int x = node.position.x() + direction.deltaX();
            int y = node.position.y() + direction.deltaY();
            return x < 0 || x > layout.size() || y < 0 || y >= layout.size() ? null : nodes.get(new GridPosition(x, y));
        }

        void connect(Node source, Node target) { source.outgoing.add(target); target.incoming.add(source); }

        List<Node> order() {
            Map<Node, Integer> degrees = new IdentityHashMap<>();
            ArrayDeque<Node> ready = new ArrayDeque<>();
            for (Node node : nodes.values()) {
                degrees.put(node, node.incoming.size());
                if (node.incoming.isEmpty()) ready.add(node);
            }
            List<Node> result = new ArrayList<>();
            while (!ready.isEmpty()) {
                Node node = ready.remove();
                result.add(node);
                for (Node target : node.outgoing) if (degrees.compute(target, (ignored, count) -> count - 1) == 0) ready.add(target);
            }
            if (result.size() != nodes.size()) {
                // Follow unresolved predecessors to report a cell in the cycle, not a downstream victim.
                Node node = nodes.values().stream().filter(value -> degrees.get(value) > 0).findFirst().orElseThrow();
                Set<Node> visited = Collections.newSetFromMap(new IdentityHashMap<>());
                while (visited.add(node)) node = node.incoming.stream().filter(value -> degrees.get(value) > 0).findFirst().orElseThrow();
                throw rejected(FactoryDiagnostic.Code.ROUTE_CYCLE, node.position, null, null, null);
            }
            return result;
        }

        void process(Node node) {
            if (node.cell == null) {
                if (!node.unprocessed.isEmpty()) {
                    var bypass = node.unprocessed.entrySet().iterator().next();
                    throw rejected(FactoryDiagnostic.Code.BYPASS_ROUTE, node.position, bypass.getKey(), BigInteger.ZERO, bypass.getValue());
                }
                if (!node.resources.isEmpty()) {
                    rows.put(node.position.y(), amounts(node.resources));
                    merge(outputs, node.resources, true);
                }
                return;
            }
            if (node.cell instanceof FactoryCell.Marker marker) {
                Map<ResourceKey, BigInteger> required = Map.of(marker.resource().resource(), marker.resource().amount());
                if (node.incoming.isEmpty()) {
                    merge(inputs, required, true);
                    merge(node.resources, required, false);
                    merge(node.unprocessed, required, false);
                } else exact(required, node.resources, node.position);
            } else if (node.cell instanceof FactoryCell.Machine machine) {
                ResolvedRecipe recipe = resolver.resolve(node.position, machine, amounts(node.resources));
                if (recipe == null) throw rejected(FactoryDiagnostic.Code.NO_RECIPE, node.position, null, null, null);
                if (!machine.routesMatch(map(recipe.outputs()).keySet())) throw rejected(FactoryDiagnostic.Code.INVALID_OUTPUT_ROUTING, node.position, null, null, null);
                BigInteger count = recipeOperations(recipe, node.resources, node.position);
                node.resources.clear();
                merge(node.resources, map(recipe.outputsForOperations(count)), false);
                node.unprocessed.clear();
                recipes.put(node.position, recipe);
                operations.put(node.position, count);
            } else if (node.resources.isEmpty()) throw rejected(FactoryDiagnostic.Code.EMPTY_ROUTE, node.position, null, null, null);

            if (node.cell instanceof FactoryCell.Machine machine) {
                for (var output : node.resources.entrySet()) {
                    Node target = neighbor(node, machine.outputDirection(output.getKey()));
                    merge(target.resources, Map.of(output.getKey(), output.getValue()), false);
                }
            } else if (node.cell instanceof FactoryCell.Arrow arrow && arrow.weights().size() > 1) {
                BigInteger total = BigInteger.valueOf(arrow.weights().values().stream().mapToInt(Integer::intValue).sum());
                for (var branch : arrow.weights().entrySet()) {
                    Node target = neighbor(node, branch.getKey());
                    merge(target.resources, split(node.resources, branch.getValue(), total), false);
                    // Provenance is only used to prevent raw inputs bypassing all processing. A mixed
                    // origin may straddle an integer split; conservatively mark every affected branch.
                    var raw = new LinkedHashMap<ResourceKey, BigInteger>();
                    node.unprocessed.forEach((key, amount) -> raw.put(key, amount.multiply(BigInteger.valueOf(branch.getValue()))
                            .add(total).subtract(BigInteger.ONE).divide(total)));
                    merge(target.unprocessed, raw, false);
                }
            } else {
                Node target = node.outgoing.getFirst();
                merge(target.resources, node.resources, false);
                merge(target.unprocessed, node.unprocessed, false);
            }
        }

        Map<ResourceKey, BigInteger> split(Map<ResourceKey, BigInteger> resources, int weight, BigInteger total) {
            Map<ResourceKey, BigInteger> result = new LinkedHashMap<>();
            for (var resource : resources.entrySet()) {
                var divided = resource.getValue().multiply(BigInteger.valueOf(weight)).divideAndRemainder(total);
                if (divided[1].signum() != 0) throw rejected(FactoryDiagnostic.Code.NON_INTEGRAL_SPLIT,
                        current, resource.getKey(), total, resource.getValue());
                result.put(resource.getKey(), divided[0]);
            }
            return result;
        }

        void merge(Map<ResourceKey, BigInteger> target, Map<ResourceKey, BigInteger> source, boolean boundary) {
            for (var entry : source.entrySet()) {
                BigInteger total = target.getOrDefault(entry.getKey(), BigInteger.ZERO).add(entry.getValue());
                BigInteger limit = boundary ? FactoryLimits.MAX_INVENTORY_AMOUNT : FactoryLimits.MAX_ENCODED_QUANTITY;
                if (total.compareTo(limit) > 0) throw rejected(FactoryDiagnostic.Code.QUANTITY_LIMIT_EXCEEDED,
                        current, entry.getKey(), limit, total.compareTo(FactoryLimits.MAX_ENCODED_QUANTITY) > 0 ? null : total);
                target.put(entry.getKey(), total);
            }
            if (target.size() > FactoryLimits.MAX_BOUNDARY_RESOURCES) throw rejected(FactoryDiagnostic.Code.RESOURCE_LIMIT_EXCEEDED,
                    current, null, BigInteger.valueOf(FactoryLimits.MAX_BOUNDARY_RESOURCES), BigInteger.valueOf(target.size()));
        }
    }

    /** Input markers determine whole native recipe operations; installed machines only set capacity. */
    public static BigInteger recipeOperations(ResolvedRecipe recipe, Map<ResourceKey, BigInteger> actual, GridPosition position) {
        BigInteger operations = null;
        for (var input : recipe.inputs()) {
            BigInteger supplied = actual.getOrDefault(input.resource(), BigInteger.ZERO);
            if (supplied.compareTo(input.amount()) < 0) throw rejected(FactoryDiagnostic.Code.MISSING_INPUT,
                    position, input.resource(), input.amount(), supplied);
            var divided = supplied.divideAndRemainder(input.amount());
            if (divided[1].signum() != 0) throw rejected(FactoryDiagnostic.Code.NON_INTEGRAL_RECIPE,
                    position, input.resource(), input.amount(), supplied);
            if (operations == null) operations = divided[0];
        }
        exact(map(recipe.inputsForOperations(operations)), actual, position);
        return operations;
    }

    public static boolean matchesRatio(ResolvedRecipe recipe, Map<ResourceKey, BigInteger> actual) {
        try { recipeOperations(recipe, actual, null); return true; }
        catch (ModelValidationException invalid) { return false; }
    }

    public static void exact(Map<ResourceKey, BigInteger> required, Map<ResourceKey, BigInteger> actual, GridPosition position) {
        Set<ResourceKey> keys = new LinkedHashSet<>(required.keySet());
        keys.addAll(actual.keySet());
        for (ResourceKey key : keys) {
            BigInteger expected = required.getOrDefault(key, BigInteger.ZERO);
            BigInteger found = actual.getOrDefault(key, BigInteger.ZERO);
            if (!expected.equals(found)) throw rejected(found.compareTo(expected) < 0
                    ? FactoryDiagnostic.Code.MISSING_INPUT : FactoryDiagnostic.Code.EXCESS_RESOURCE, position, key, expected, found);
        }
    }

    public static Map<ResourceKey, BigInteger> map(List<ResourceAmount> resources) {
        Map<ResourceKey, BigInteger> result = new LinkedHashMap<>();
        for (ResourceAmount amount : resources) result.merge(amount.resource(), amount.amount(), BigInteger::add);
        return result;
    }

    private static List<ResourceAmount> amounts(Map<ResourceKey, BigInteger> resources) {
        return resources.entrySet().stream().map(entry -> new ResourceAmount(entry.getKey(), entry.getValue())).toList();
    }
}
