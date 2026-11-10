package com.aquariverr.fluxod.miniature.runtime;

import com.aquariverr.fluxod.miniature.model.FactoryLimits;
import com.aquariverr.fluxod.miniature.model.FactoryRules;

import java.math.BigInteger;
import java.util.List;

/** Immutable round boundaries where completed work reduces the number of active machines. */
public record BatchEnergySchedule(List<Stage> stages) {
    public static final int MAX_STAGES = 2 * FactoryLimits.MAX_GRID_SIZE * FactoryLimits.MAX_GRID_SIZE + 1;

    public BatchEnergySchedule {
        stages = List.copyOf(stages);
        if (stages.isEmpty() || stages.size() > MAX_STAGES || stages.getFirst().round() != 0)
            throw new IllegalArgumentException("Invalid energy schedule");
        for (int i = 1; i < stages.size(); i++) {
            if (stages.get(i).round() <= stages.get(i - 1).round()
                    || stages.get(i).machines() >= stages.get(i - 1).machines())
                throw new IllegalArgumentException("Energy schedule must decrease as work completes");
        }
    }

    public static BatchEnergySchedule fixed(BigInteger energyPerTick) {
        if (energyPerTick == null || energyPerTick.signum() <= 0
                || energyPerTick.compareTo(FactoryRules.energyPerTick(FactoryLimits.MAX_TOTAL_MACHINES)) > 0
                || energyPerTick.remainder(FactoryRules.ENERGY_PER_MACHINE_TICK).signum() != 0)
            throw new IllegalArgumentException("Invalid machine fee");
        return new BatchEnergySchedule(List.of(new Stage(0, energyPerTick.divide(FactoryRules.ENERGY_PER_MACHINE_TICK).intValueExact())));
    }

    public BigInteger energyPerTick(int paidTicks) {
        if (paidTicks < 0) throw new IllegalArgumentException("Negative progress");
        int round = paidTicks / FactoryRules.BATCH_TICKS;
        Stage active = stages.getFirst();
        for (Stage stage : stages) {
            if (stage.round() > round) break;
            active = stage;
        }
        return FactoryRules.energyPerTick(active.machines());
    }

    public void validateDuration(int requiredTicks) {
        if (requiredTicks < FactoryRules.BATCH_TICKS || requiredTicks > FactoryLimits.MAX_BATCH_TICKS
                || requiredTicks % FactoryRules.BATCH_TICKS != 0
                || stages.getLast().round() >= requiredTicks / FactoryRules.BATCH_TICKS)
            throw new IllegalArgumentException("Energy schedule exceeds batch duration");
    }

    public BigInteger totalEnergy(int requiredTicks) {
        validateDuration(requiredTicks);
        BigInteger total = BigInteger.ZERO;
        for (int i = 0; i < stages.size(); i++) {
            Stage stage = stages.get(i);
            int endRound = i + 1 < stages.size() ? stages.get(i + 1).round() : requiredTicks / FactoryRules.BATCH_TICKS;
            total = total.add(FactoryRules.energyPerTick(stage.machines())
                    .multiply(BigInteger.valueOf((long) (endRound - stage.round()) * FactoryRules.BATCH_TICKS)));
        }
        return total;
    }

    public record Stage(int round, int machines) {
        public Stage {
            if (round < 0 || round >= FactoryLimits.MAX_BATCH_TICKS / FactoryRules.BATCH_TICKS
                    || machines < 1 || machines > FactoryLimits.MAX_TOTAL_MACHINES)
                throw new IllegalArgumentException("Invalid active machine count or round");
        }
    }
}
