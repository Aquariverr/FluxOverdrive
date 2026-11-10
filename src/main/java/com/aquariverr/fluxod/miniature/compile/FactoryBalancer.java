package com.aquariverr.fluxod.miniature.compile;

import com.aquariverr.fluxod.miniature.model.*;
import com.aquariverr.fluxod.miniature.recipe.ResolvedRecipe;
import java.math.BigInteger;
import java.util.*;
import static com.aquariverr.fluxod.miniature.compile.BalanceFraction.*;
import static com.aquariverr.fluxod.miniature.compile.FactoryCompiler.rejected;
import static com.aquariverr.fluxod.miniature.model.FactoryDiagnostic.Code.*;

/** Server-derived positive integer balance, preserving topology, branch weights and real machines. */
public final class FactoryBalancer {
    public static final int MAX_ATTEMPTS = 64;
    @FunctionalInterface public interface RecipeCandidates {
        List<ResolvedRecipe> candidates(GridPosition position, FactoryCell.Machine machine, List<ResourceKey> incoming);
    }
    public record Result(FactoryLayout layout, CompilationResult compilation) {
        public boolean successful() { return compilation.successful(); }
    }
    private record Value(FactoryTopology.Node node, BalanceFraction[] expression) {}

    public static Result balance(FactoryLayout layout, long generation, RecipeCandidates resolver) {
        if (generation < 1) throw new IllegalArgumentException("Invalid recipe generation");
        Objects.requireNonNull(resolver);
        FactoryTopology topology;
        try { topology = new FactoryTopology(layout); }
        catch (ModelValidationException error) { return failure(layout, error.diagnostic()); }
        var choices = new ArrayList<Integer>();
        var budget = new PositiveBalanceSolver.Budget();
        FactoryDiagnostic diagnostic = new FactoryDiagnostic(BALANCE_IMPOSSIBLE);
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            var pass = new Pass(layout, topology, resolver, choices, budget);
            try {
                FactoryLayout balanced = pass.run();
                var compiled = FactoryCompiler.compile(balanced, generation, (position, machine, inputs) -> pass.recipes.get(position));
                if (compiled.successful()) return new Result(balanced, compiled);
                diagnostic = compiled.diagnostic();
            } catch (ModelValidationException error) {
                diagnostic = error.diagnostic();
                if (diagnostic.position().isEmpty() && pass.current != null) diagnostic = new FactoryDiagnostic(diagnostic.code(),
                        Optional.of(pass.current), diagnostic.resource(), diagnostic.expected(), diagnostic.actual());
                if (diagnostic.code() == BALANCE_SEARCH_LIMIT) return failure(layout, diagnostic);
            }
            boolean next = false;
            for (int i = pass.options.size() - 1; i >= 0; i--) if (choices.get(i) + 1 < pass.options.get(i)) {
                choices.set(i, choices.get(i) + 1);
                while (choices.size() > i + 1) choices.removeLast();
                next = true; break;
            }
            if (!next) return failure(layout, diagnostic);
        }
        return failure(layout, new FactoryDiagnostic(BALANCE_SEARCH_LIMIT));
    }

    private static Result failure(FactoryLayout layout, FactoryDiagnostic diagnostic) {
        return new Result(layout, CompilationResult.failure(diagnostic));
    }

    private static final class Pass {
        final FactoryLayout layout;
        final FactoryTopology topology;
        final RecipeCandidates resolver;
        final List<Integer> choices, options = new ArrayList<>();
        final PositiveBalanceSolver.Budget budget;
        final Map<FactoryTopology.Node, Integer> variables = new LinkedHashMap<>();
        final Map<FactoryTopology.Node, Map<ResourceKey, BalanceFraction[]>> incoming = new IdentityHashMap<>();
        final Map<FactoryTopology.Node, Set<ResourceKey>> raw = new IdentityHashMap<>();
        final Map<GridPosition, ResolvedRecipe> recipes = new LinkedHashMap<>();
        final List<Value> integerValues = new ArrayList<>();
        final Map<FactoryTopology.Node, BalanceFraction[]> markers = new LinkedHashMap<>();
        final int[] parents;
        GridPosition current;
        final PositiveBalanceSolver solver;

        Pass(FactoryLayout layout, FactoryTopology topology, RecipeCandidates resolver, List<Integer> choices,
             PositiveBalanceSolver.Budget budget) {
            this.layout = layout; this.topology = topology; this.resolver = resolver; this.choices = choices; this.budget = budget;
            parents = new int[(layout.size() + 1) * layout.size()];
            for (int i = 0; i < parents.length; i++) parents[i] = i;
            for (var node : topology.nodes.values()) {
                incoming.put(node, new LinkedHashMap<>()); raw.put(node, new LinkedHashSet<>());
                if (node.cell instanceof FactoryCell.Machine || node.cell instanceof FactoryCell.Marker && node.incoming.isEmpty())
                    variables.put(node, variables.size());
                for (var target : node.outgoing.keySet()) parents[root(target)] = root(node);
            }
            solver = new PositiveBalanceSolver(variables.size(), budget);
        }

        FactoryLayout run() {
            for (var node : topology.ordered) process(node);
            BalanceFraction[] solution = solver.solve();
            // Include operations and every routed branch amount, not only external marker quantities.
            variables.forEach((node, variable) -> integerValues.add(new Value(node, variable(variable, BigInteger.ONE))));
            Map<Integer, BigInteger> scales = new HashMap<>(), divisors = new HashMap<>();
            for (Value value : integerValues) {
                BigInteger denominator = evaluate(value.expression, solution).denominator();
                int root = root(value.node);
                BigInteger previous = scales.getOrDefault(root, BigInteger.ONE);
                BigInteger lcm = previous.divide(previous.gcd(denominator)).multiply(denominator);
                if (lcm.bitLength() > 4096) throw rejected(BALANCE_SEARCH_LIMIT, value.node.position, null, null, null);
                scales.put(root, lcm);
            }
            for (Value value : integerValues) {
                int root = root(value.node);
                BigInteger quantity = integer(value.expression, solution, scales.get(root));
                divisors.merge(root, quantity, BigInteger::gcd);
            }
            var cells = new ArrayList<>(layout.cells());
            for (var entry : markers.entrySet()) {
                var node = entry.getKey(); current = node.position;
                var marker = (FactoryCell.Marker) node.cell;
                BigInteger amount = integer(entry.getValue(), solution, scales.get(root(node))).divide(divisors.get(root(node)));
                cells.set(node.position.y() * layout.size() + node.position.x(), new FactoryCell.Marker(
                        new ResourceAmount(marker.resource().resource(), amount), marker.direction()));
            }
            // Pin inferred choices so future explicit compilation uses the same native recipe.
            recipes.forEach((position, recipe) -> {
                var machine = (FactoryCell.Machine) layout.cellAt(position);
                cells.set(position.y() * layout.size() + position.x(), new FactoryCell.Machine(machine.stack(), recipe.reference(), machine.direction(), machine.outputDirections()));
            });
            if (cells.equals(layout.cells())) return layout;
            if (layout.revision() == Long.MAX_VALUE) throw rejected(INVALID_REVISION, null, null, null, null);
            return new FactoryLayout(layout.size(), cells, layout.revision() + 1);
        }

        void process(FactoryTopology.Node node) {
            current = node.position;
            Map<ResourceKey, BalanceFraction[]> flow = incoming.get(node);
            flow.values().forEach(expression -> integerValues.add(new Value(node, expression)));
            if (node.cell == null) {
                if (!raw.get(node).isEmpty()) throw rejected(BYPASS_ROUTE, current, raw.get(node).iterator().next(), null, null);
                return;
            }
            if (node.cell instanceof FactoryCell.Marker marker) {
                ResourceKey key = marker.resource().resource();
                if (node.incoming.isEmpty()) {
                    flow.put(key, variable(variables.get(node), BigInteger.ONE)); raw.get(node).add(key);
                } else if (!flow.containsKey(key)) throw rejected(MISSING_INPUT, current, key, null, null);
                for (ResourceKey extra : flow.keySet()) if (!extra.equals(key)) throw rejected(EXCESS_RESOURCE, current, extra, null, null);
                markers.put(node, flow.get(key));
            } else if (node.cell instanceof FactoryCell.Machine machine) {
                List<ResolvedRecipe> candidates = resolver.candidates(current, machine, List.copyOf(flow.keySet()));
                if (machine.recipe() != null) candidates = candidates.stream().filter(recipe -> recipe.reference().equals(machine.recipe())).toList();
                int index = options.size();
                options.add(candidates.size()); if (choices.size() <= index) choices.add(0);
                if (candidates.isEmpty()) throw rejected(machine.recipe() == null ? NO_RECIPE : INVALID_RECIPE, current, null, null, null);
                ResolvedRecipe recipe = candidates.get(choices.get(index)); recipes.put(current, recipe);
                if (!machine.routesMatch(FactoryCompiler.map(recipe.outputs()).keySet())) throw rejected(INVALID_OUTPUT_ROUTING, current, null, null, null);
                var required = FactoryCompiler.map(recipe.inputs());
                var resources = new LinkedHashSet<>(flow.keySet()); resources.addAll(required.keySet());
                for (ResourceKey resource : resources) {
                    var equation = flow.getOrDefault(resource, zero()).clone();
                    int variable = variables.get(node);
                    equation[variable] = equation[variable].subtract(of(required.getOrDefault(resource, BigInteger.ZERO)));
                    solver.add(equation, current, resource);
                }
                flow = new LinkedHashMap<>();
                for (var output : recipe.outputs()) flow.put(output.resource(), variable(variables.get(node), output.amount()));
                raw.get(node).clear();
            } else if (flow.isEmpty()) throw rejected(EMPTY_ROUTE, current, null, null, null);
            int totalWeight = node.outgoing.values().stream().mapToInt(Integer::intValue).sum();
            for (var edge : node.outgoing.entrySet()) {
                var target = incoming.get(edge.getKey());
                BalanceFraction fraction = node.cell instanceof FactoryCell.Machine ? ONE : of(edge.getValue()).divide(of(totalWeight));
                for (var resource : flow.entrySet()) {
                    if (node.cell instanceof FactoryCell.Machine machine) {
                        var direction = machine.outputDirection(resource.getKey());
                        if (node.position.x() + direction.deltaX() != edge.getKey().position.x()
                                || node.position.y() + direction.deltaY() != edge.getKey().position.y()) continue;
                    }
                    var branch = resource.getValue().clone(); budget.use(branch.length);
                    for (int j = 0; j < branch.length; j++) branch[j] = branch[j].multiply(fraction);
                    integerValues.add(new Value(node, branch));
                    var sum = target.computeIfAbsent(resource.getKey(), ignored -> zero());
                    for (int j = 0; j < sum.length; j++) sum[j] = sum[j].add(branch[j]);
                }
                raw.get(edge.getKey()).addAll(raw.get(node));
                if (target.size() > FactoryLimits.MAX_BOUNDARY_RESOURCES) throw rejected(RESOURCE_LIMIT_EXCEEDED, current, null, null, null);
            }
        }

        BalanceFraction[] zero() { var result = new BalanceFraction[variables.size()]; Arrays.fill(result, ZERO); return result; }
        BalanceFraction[] variable(int index, BigInteger amount) { var result = zero(); result[index] = of(amount); return result; }
        BalanceFraction evaluate(BalanceFraction[] expression, BalanceFraction[] values) {
            budget.use(expression.length); BalanceFraction result = ZERO;
            for (int i = 0; i < expression.length; i++) if (!expression[i].zero()) result = result.add(expression[i].multiply(values[i]));
            return result;
        }
        BigInteger integer(BalanceFraction[] expression, BalanceFraction[] values, BigInteger scale) {
            BalanceFraction value = evaluate(expression, values);
            return value.numerator().multiply(scale).divide(value.denominator());
        }
        int root(FactoryTopology.Node node) {
            int index = node.position.y() * (layout.size() + 1) + node.position.x();
            while (parents[index] != index) { parents[index] = parents[parents[index]]; index = parents[index]; }
            return index;
        }
    }
    private FactoryBalancer() {}
}
