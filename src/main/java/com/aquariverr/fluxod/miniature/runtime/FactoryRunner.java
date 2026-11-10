package com.aquariverr.fluxod.miniature.runtime;

import com.aquariverr.fluxod.miniature.compile.CompiledFactory;
import com.aquariverr.fluxod.miniature.model.FactoryLimits;
import com.aquariverr.fluxod.miniature.model.FactoryRules;
import com.aquariverr.fluxod.miniature.model.ResourceAmount;
import com.aquariverr.fluxod.miniature.model.ResourceKey;
import com.aquariverr.fluxod.miniature.storage.FactoryStorage;
import com.aquariverr.fluxod.miniature.storage.ResourceInventory;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/** Prepares immutable state; the owner encodes it before atomically paying and publishing it. */
public final class FactoryRunner {
    private FactoryRunner() {}

    public enum Status {
        DISABLED, REDSTONE, NO_BATTERY, NOT_COMPILED, INVALID_ENVIRONMENT, MISSING_INPUT, OUTPUT_FULL,
        NO_ENERGY, RUNNING, STATE_TOO_LARGE, READY;

        public String translationKey() {
            return "message.flux_overdrive.miniature_universe.runtime." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public record Step(FactoryStorage storage, BigInteger charge, Status status) {}

    public static Step prepare(FactoryStorage storage, CompiledFactory compiled, long generation,
                               FactoryRules.Environment environment, long gameTime) {
        if (!storage.enabled()) return waiting(storage, Status.DISABLED);
        if (storage.lastPaidGameTime() == gameTime) return waiting(storage, Status.READY);
        RunningBatch batch = storage.batch();
        if (batch == null) {
            if (compiled == null || !compiled.matches(storage.layout(), generation)) return waiting(storage, Status.NOT_COMPILED);
            if (environment == null) return waiting(storage, Status.INVALID_ENVIRONMENT);
            // The same exact resource can be both consumed and locked; it cannot fund both independently.
            var required = new LinkedHashMap<ResourceKey, BigInteger>();
            for (var amounts : List.of(compiled.inputs(), compiled.catalysts())) {
                for (var amount : amounts) required.merge(amount.resource(), amount.amount(), BigInteger::add);
            }
            BigInteger parallel = BigInteger.valueOf(compiled.maxParallelism(environment.parallelism()));
            for (var input : required.entrySet()) {
                parallel = parallel.min(storage.inputs().amount(input.getKey()).divide(input.getValue()));
            }
            if (parallel.signum() == 0) return waiting(storage, Status.MISSING_INPUT);
            var kinds = new HashSet<>(storage.outputs().amounts().keySet());
            for (ResourceAmount output : compiled.outputs()) {
                kinds.add(output.resource());
                parallel = parallel.min(FactoryLimits.MAX_INVENTORY_AMOUNT.subtract(storage.outputs().amount(output.resource()))
                        .divide(output.amount()));
            }
            if (parallel.signum() == 0 || kinds.size() > FactoryLimits.MAX_BOUNDARY_RESOURCES) return waiting(storage, Status.OUTPUT_FULL);
            var energySchedule = compiled.energySchedule(parallel.intValueExact(), environment.parallelism());
            batch = new RunningBatch(UUID.randomUUID(), compiled.fingerprint(), environment, parallel.intValueExact(),
                    scaled(compiled.inputs(), parallel), scaled(compiled.outputs(), parallel),
                    scaled(compiled.catalysts(), parallel), energySchedule.energyPerTick(0), 0,
                    compiled.requiredTicks(parallel.intValueExact(), environment.parallelism()), energySchedule);
            ResourceInventory remaining = storage.inputs();
            for (var input : required.entrySet()) {
                remaining = remaining.extract(input.getKey(), input.getValue().multiply(parallel)).inventory();
            }
            storage = storage.withInputs(remaining);
        }
        BigInteger charge = batch.currentEnergyPerTick();
        RunningBatch paid = batch.paidTicks() == batch.requiredTicks() ? batch : batch.paid();
        FactoryStorage next = storage.withBatch(paid, gameTime);
        if (paid.paidTicks() == paid.requiredTicks()) next = next.completeBatch();
        return new Step(next, charge, Status.RUNNING);
    }

    private static Step waiting(FactoryStorage storage, Status status) { return new Step(storage, BigInteger.ZERO, status); }

    private static ResourceInventory scaled(List<ResourceAmount> amounts, BigInteger parallel) {
        var result = new LinkedHashMap<ResourceKey, BigInteger>();
        for (ResourceAmount amount : amounts) result.merge(amount.resource(), amount.amount().multiply(parallel), BigInteger::add);
        return new ResourceInventory(result);
    }
}
