package com.aquariverr.fluxod.miniature.compile;

import com.aquariverr.fluxod.miniature.model.FactoryLayout;
import com.aquariverr.fluxod.miniature.model.FactoryRules;
import com.aquariverr.fluxod.miniature.model.GridPosition;
import com.aquariverr.fluxod.miniature.model.ResourceAmount;
import com.aquariverr.fluxod.miniature.recipe.ResolvedRecipe;
import com.aquariverr.fluxod.miniature.runtime.BatchEnergySchedule;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Server-derived base recipe, before environmental parallelism. Never persisted or accepted from clients. */
public record CompiledFactory(FactoryLayout layout, String fingerprint, long recipeGeneration,
                              List<ResourceAmount> inputs, List<ResourceAmount> outputs,
                              List<ResourceAmount> catalysts, Map<Integer, List<ResourceAmount>> outputRows,
                              Map<GridPosition, ResolvedRecipe> recipes, Map<GridPosition, BigInteger> operations) {
    public CompiledFactory(FactoryLayout layout, String fingerprint, long recipeGeneration,
                           List<ResourceAmount> inputs, List<ResourceAmount> outputs, List<ResourceAmount> catalysts,
                           Map<Integer, List<ResourceAmount>> outputRows, Map<GridPosition, ResolvedRecipe> recipes) {
        this(layout, fingerprint, recipeGeneration, inputs, outputs, catalysts, outputRows, recipes, legacyOperations(layout, recipes));
    }
    public CompiledFactory {
        if (layout == null || fingerprint == null || recipeGeneration < 1) throw new IllegalArgumentException("Invalid compilation identity");
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
        catalysts = List.copyOf(catalysts);
        Map<Integer, List<ResourceAmount>> rows = new LinkedHashMap<>();
        outputRows.forEach((row, resources) -> rows.put(row, List.copyOf(resources)));
        outputRows = Collections.unmodifiableMap(rows);
        recipes = Collections.unmodifiableMap(new LinkedHashMap<>(recipes));
        operations = Collections.unmodifiableMap(new LinkedHashMap<>(operations));
        if (!operations.keySet().equals(recipes.keySet())) throw new IllegalArgumentException("Missing machine work");
        operations.values().forEach(com.aquariverr.fluxod.miniature.model.FactoryLimits::requirePositiveQuantity);
    }

    public int machineCount() { return layout.machineCount(); }
    public BigInteger energyPerTick() { return energySchedule(1, 1).energyPerTick(0); }
    public BigInteger energyPerBatch() { return energySchedule(1, 1).totalEnergy(requiredTicks(1, 1)); }
    public boolean matches(FactoryLayout current, long generation) { return recipeGeneration == generation && layout.equals(current); }

    public BatchEnergySchedule energySchedule(int parallelism, int environmentParallelism) {
        int rounds = requiredTicks(parallelism, environmentParallelism) / FactoryRules.BATCH_TICKS;
        var changes = new java.util.TreeMap<Integer, Integer>();
        for (var work : operations.entrySet()) {
            int machines = ((com.aquariverr.fluxod.miniature.model.FactoryCell.Machine) layout.cellAt(work.getKey())).stack().count();
            BigInteger perMachine = recipes.get(work.getKey()).normalization().multiply(BigInteger.valueOf(environmentParallelism));
            // Round up only at the machine level; surplus installed machines never become active.
            BigInteger machineRounds = work.getValue().multiply(BigInteger.valueOf(parallelism))
                    .add(perMachine).subtract(BigInteger.ONE).divide(perMachine);
            BigInteger[] divided = machineRounds.divideAndRemainder(BigInteger.valueOf(machines));
            int fullRounds = divided[0].intValueExact(), remainder = divided[1].intValueExact();
            changes.merge(0, fullRounds > 0 ? machines : remainder, Integer::sum);
            if (fullRounds > 0) changes.merge(fullRounds, remainder - machines, Integer::sum);
            if (remainder > 0) changes.merge(fullRounds + 1, -remainder, Integer::sum);
        }
        var stages = new java.util.ArrayList<BatchEnergySchedule.Stage>();
        int active = 0;
        for (var change : changes.entrySet()) {
            if (change.getKey() >= rounds) break;
            active += change.getValue();
            if (change.getValue() != 0) stages.add(new BatchEnergySchedule.Stage(change.getKey(), active));
        }
        return new BatchEnergySchedule(stages);
    }

    public int maxParallelism(int environmentParallelism) {
        if (environmentParallelism < 1 || environmentParallelism > 2000) throw new IllegalArgumentException("Invalid environment capacity");
        if (operations.isEmpty()) return environmentParallelism;
        BigInteger result = BigInteger.valueOf(com.aquariverr.fluxod.miniature.model.FactoryLimits.MAX_BATCH_PARALLELISM);
        for (var work : operations.entrySet()) result = result.min(capacity(work.getKey(), environmentParallelism).divide(work.getValue()));
        // Oversized base batches are processed in successive rounds without splitting their assets.
        return result.max(BigInteger.ONE).intValueExact();
    }

    public int requiredTicks(int parallelism, int environmentParallelism) {
        if (parallelism < 1 || environmentParallelism < 1 || environmentParallelism > 2000) throw new IllegalArgumentException("Invalid capacity");
        BigInteger rounds = BigInteger.ONE;
        for (var work : operations.entrySet()) {
            BigInteger capacity = capacity(work.getKey(), environmentParallelism);
            BigInteger demand = work.getValue().multiply(BigInteger.valueOf(parallelism));
            rounds = rounds.max(demand.add(capacity).subtract(BigInteger.ONE).divide(capacity));
        }
        BigInteger ticks = rounds.multiply(BigInteger.valueOf(FactoryRules.BATCH_TICKS));
        if (ticks.compareTo(BigInteger.valueOf(com.aquariverr.fluxod.miniature.model.FactoryLimits.MAX_BATCH_TICKS)) > 0) {
            throw FactoryCompiler.rejected(com.aquariverr.fluxod.miniature.model.FactoryDiagnostic.Code.DURATION_LIMIT_EXCEEDED,
                    null, null, BigInteger.valueOf(com.aquariverr.fluxod.miniature.model.FactoryLimits.MAX_BATCH_TICKS),
                    ticks.compareTo(com.aquariverr.fluxod.miniature.model.FactoryLimits.MAX_ENCODED_QUANTITY) <= 0 ? ticks : null);
        }
        return ticks.intValueExact();
    }

    private BigInteger capacity(GridPosition position, int environmentParallelism) {
        int machines = ((com.aquariverr.fluxod.miniature.model.FactoryCell.Machine) layout.cellAt(position)).stack().count();
        return recipes.get(position).normalization().multiply(BigInteger.valueOf(machines)).multiply(BigInteger.valueOf(environmentParallelism));
    }

    private static Map<GridPosition, BigInteger> legacyOperations(FactoryLayout layout, Map<GridPosition, ResolvedRecipe> recipes) {
        Map<GridPosition, BigInteger> result = new LinkedHashMap<>();
        recipes.forEach((position, recipe) -> result.put(position, recipe.normalization().multiply(BigInteger.valueOf(
                ((com.aquariverr.fluxod.miniature.model.FactoryCell.Machine) layout.cellAt(position)).stack().count()))));
        return result;
    }
}
