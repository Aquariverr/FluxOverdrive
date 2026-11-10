package com.aquariverr.fluxod.miniature.runtime;

import com.aquariverr.fluxod.miniature.model.FactoryLimits;
import com.aquariverr.fluxod.miniature.model.FactoryRules;
import com.aquariverr.fluxod.miniature.storage.ResourceInventory;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Objects;
import java.util.UUID;

/** Consumed inputs, reserved outputs and unchanged catalysts belong to this batch until its single commit. */
public record RunningBatch(UUID id, String fingerprint, FactoryRules.Environment environment, int parallelism,
                           ResourceInventory inputs, ResourceInventory outputs, ResourceInventory catalysts,
                           BigInteger energyPerTick, int paidTicks, int requiredTicks, BatchEnergySchedule energySchedule) {
    public RunningBatch(UUID id, String fingerprint, FactoryRules.Environment environment, int parallelism,
                        ResourceInventory inputs, ResourceInventory outputs, ResourceInventory catalysts,
                        BigInteger energyPerTick, int paidTicks, int requiredTicks) {
        this(id, fingerprint, environment, parallelism, inputs, outputs, catalysts, energyPerTick, paidTicks,
                requiredTicks, BatchEnergySchedule.fixed(energyPerTick));
    }
    public RunningBatch(UUID id, String fingerprint, FactoryRules.Environment environment, int parallelism,
                        ResourceInventory inputs, ResourceInventory outputs, ResourceInventory catalysts,
                        BigInteger energyPerTick, int paidTicks) {
        this(id, fingerprint, environment, parallelism, inputs, outputs, catalysts, energyPerTick, paidTicks, FactoryRules.BATCH_TICKS);
    }
    public RunningBatch(UUID id, String fingerprint, FactoryRules.Environment environment, int parallelism,
                        ResourceInventory inputs, ResourceInventory outputs, BigInteger energyPerTick, int paidTicks) {
        this(id, fingerprint, environment, parallelism, inputs, outputs, ResourceInventory.empty(), energyPerTick, paidTicks);
    }

    public RunningBatch {
        Objects.requireNonNull(id);
        Objects.requireNonNull(environment);
        Objects.requireNonNull(inputs);
        Objects.requireNonNull(outputs);
        Objects.requireNonNull(catalysts);
        Objects.requireNonNull(energySchedule);
        energySchedule.validateDuration(requiredTicks);
        if (!energySchedule.energyPerTick(0).equals(energyPerTick)) throw new IllegalArgumentException("Inconsistent initial batch fee");
        if (fingerprint == null || !fingerprint.matches("[0-9a-f]{64}") || parallelism < 1
                || parallelism > (long) environment.parallelism() * 64 * 1000 || paidTicks < 0 || paidTicks > requiredTicks
                || requiredTicks < FactoryRules.BATCH_TICKS || requiredTicks > FactoryLimits.MAX_BATCH_TICKS
                || requiredTicks % FactoryRules.BATCH_TICKS != 0
                || outputs.amounts().isEmpty() || energyPerTick == null || energyPerTick.signum() <= 0
                || energyPerTick.compareTo(FactoryRules.energyPerTick(FactoryLimits.MAX_TOTAL_MACHINES)) > 0
                || energyPerTick.remainder(FactoryRules.ENERGY_PER_MACHINE_TICK).signum() != 0) {
            throw new IllegalArgumentException("Invalid running batch");
        }
        var boundary = new HashSet<>(inputs.amounts().keySet());
        boundary.addAll(outputs.amounts().keySet());
        boundary.addAll(catalysts.amounts().keySet());
        if (boundary.size() > FactoryLimits.MAX_BOUNDARY_RESOURCES) throw new IllegalArgumentException("Batch resource limit exceeded");
        BigInteger multiplier = BigInteger.valueOf(parallelism);
        if (inputs.amounts().values().stream().anyMatch(amount -> amount.remainder(multiplier).signum() != 0)
                || outputs.amounts().values().stream().anyMatch(amount -> amount.remainder(multiplier).signum() != 0)
                || catalysts.amounts().values().stream().anyMatch(amount -> amount.remainder(multiplier).signum() != 0)) {
            throw new IllegalArgumentException("Batch amounts do not match its parallelism");
        }
        for (var entry : catalysts.amounts().entrySet()) {
            FactoryLimits.requireInventoryQuantity(inputs.amount(entry.getKey()).add(entry.getValue()));
        }
    }

    public RunningBatch paid() {
        return new RunningBatch(id, fingerprint, environment, parallelism, inputs, outputs, catalysts, energyPerTick, paidTicks + 1, requiredTicks, energySchedule);
    }

    public BigInteger currentEnergyPerTick() { return paidTicks == requiredTicks ? BigInteger.ZERO : energySchedule.energyPerTick(paidTicks); }
    public BigInteger totalEnergy() { return energySchedule.totalEnergy(requiredTicks); }
}
