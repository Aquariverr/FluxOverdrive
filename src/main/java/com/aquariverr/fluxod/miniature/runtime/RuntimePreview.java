package com.aquariverr.fluxod.miniature.runtime;

import com.aquariverr.fluxod.miniature.model.FactoryLimits;
import com.aquariverr.fluxod.miniature.model.FactoryRules;
import com.aquariverr.fluxod.miniature.storage.FactoryStorage;

import java.math.BigInteger;
import java.util.Objects;

/** Bounded display state, without executable inputs, reserved assets or batch identity. */
public record RuntimePreview(boolean enabled, RedstoneMode redstoneMode, FactoryRunner.Status status,
                             int environmentParallelism, int batchParallelism, int paidTicks, BigInteger energyPerTick, int requiredTicks,
                             BigInteger totalEnergy) {
    public RuntimePreview(boolean enabled, RedstoneMode redstoneMode, FactoryRunner.Status status,
                          int environmentParallelism, int batchParallelism, int paidTicks, BigInteger energyPerTick, int requiredTicks) {
        this(enabled, redstoneMode, status, environmentParallelism, batchParallelism, paidTicks, energyPerTick, requiredTicks,
                energyPerTick.multiply(BigInteger.valueOf(requiredTicks)));
    }
    public RuntimePreview(boolean enabled, RedstoneMode redstoneMode, FactoryRunner.Status status,
                          int environmentParallelism, int batchParallelism, int paidTicks, BigInteger energyPerTick) {
        this(enabled, redstoneMode, status, environmentParallelism, batchParallelism, paidTicks, energyPerTick, FactoryRules.BATCH_TICKS);
    }
    public static final RuntimePreview NONE = new RuntimePreview(false, RedstoneMode.IGNORE, FactoryRunner.Status.DISABLED, 0, 0, 0, BigInteger.ZERO);

    public RuntimePreview {
        Objects.requireNonNull(redstoneMode);
        Objects.requireNonNull(status);
        Objects.requireNonNull(energyPerTick);
        Objects.requireNonNull(totalEnergy);
        if (totalEnergy.signum() < 0 || totalEnergy.compareTo(FactoryRules.energyPerTick(FactoryLimits.MAX_TOTAL_MACHINES)
                .multiply(BigInteger.valueOf(requiredTicks))) > 0) throw new IllegalArgumentException("Invalid batch energy");
        if (environmentParallelism < 0 || environmentParallelism > 2000 || batchParallelism < 0 || batchParallelism > FactoryLimits.MAX_BATCH_PARALLELISM
                || requiredTicks < 20 || requiredTicks > FactoryLimits.MAX_BATCH_TICKS || requiredTicks % 20 != 0
                || paidTicks < 0 || paidTicks > requiredTicks || batchParallelism == 0 && paidTicks != 0 || energyPerTick.signum() < 0
                || energyPerTick.compareTo(FactoryRules.energyPerTick(FactoryLimits.MAX_TOTAL_MACHINES)) > 0) {
            throw new IllegalArgumentException("Invalid runtime preview");
        }
    }

    public static RuntimePreview from(FactoryStorage storage, FactoryRunner.Status status, int environmentParallelism) {
        RunningBatch batch = storage.batch();
        return new RuntimePreview(storage.enabled(), storage.redstoneMode(), status, environmentParallelism,
                batch == null ? 0 : batch.parallelism(), batch == null ? 0 : batch.paidTicks(),
                batch == null ? BigInteger.ZERO : batch.currentEnergyPerTick(),
                batch == null ? FactoryRules.BATCH_TICKS : batch.requiredTicks(), batch == null ? BigInteger.ZERO : batch.totalEnergy());
    }

    public boolean locked() { return batchParallelism != 0; }
}
