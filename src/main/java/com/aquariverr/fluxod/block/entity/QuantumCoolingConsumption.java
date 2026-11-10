package com.aquariverr.fluxod.block.entity;

import com.aquariverr.fluxod.Config;

final class QuantumCoolingConsumption {

    private QuantumCoolingConsumption() {
    }

    static long effectiveCooling(long storedCooling) {
        return effectiveCooling(storedCooling, Config.quantumCoolantConsumptionMultiplier);
    }

    static long storedCoolingFor(long effectiveCooling) {
        return storedCoolingFor(effectiveCooling, Config.quantumCoolantConsumptionMultiplier);
    }

    static long effectiveCooling(long storedCooling, int multiplier) {
        if (storedCooling <= 0L) return 0L;
        return storedCooling / Math.max(1, multiplier);
    }

    static long storedCoolingFor(long effectiveCooling, int multiplier) {
        if (effectiveCooling <= 0L) return 0L;
        long normalizedMultiplier = Math.max(1, multiplier);
        return effectiveCooling > Long.MAX_VALUE / normalizedMultiplier
                ? Long.MAX_VALUE
                : effectiveCooling * normalizedMultiplier;
    }
}
