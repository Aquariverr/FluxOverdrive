package com.aquariverr.fluxod.miniature.model;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class FactoryRules {
    public static final int BATCH_TICKS = 20;
    public static final BigInteger ENERGY_PER_MACHINE_TICK = BigInteger.valueOf(20_000_000);

    private FactoryRules() {
    }

    public static int stabilityBand(int stability) {
        int clamped = Math.max(0, Math.min(10_000, stability));
        return Math.max(1, (clamped + 999) / 1_000);
    }

    public static int temperatureBand(double kelvin) {
        if (!Double.isFinite(kelvin)) {
            throw new ModelValidationException(FactoryDiagnostic.Code.INVALID_ENVIRONMENT,
                    "Temperature must be finite");
        }
        for (int band = 1; band < 10; band++) {
            if (kelvin <= band * 4_000.0) {
                return band;
            }
        }
        return 10;
    }

    public static Environment environment(int stability, double kelvin) {
        return new Environment(stabilityBand(stability), temperatureBand(kelvin));
    }

    public static int parallelism(int n, int m) {
        requireBand(n);
        requireBand(m);
        int inverseStability = 11 - n;
        return inverseStability * inverseStability * inverseStability + m * m * m;
    }

    public static BigInteger normalizationFactor(List<ResourceAmount> outputs) {
        Map<ResourceKey, BigInteger> merged = merge(outputs);
        if (merged.isEmpty()) {
            throw new ModelValidationException(FactoryDiagnostic.Code.EMPTY_OUTPUTS,
                    "A recipe must have at least one deterministic output");
        }
        BigInteger factor = BigInteger.ONE;
        for (Map.Entry<ResourceKey, BigInteger> output : merged.entrySet()) {
            BigInteger minimum = output.getKey().kind().minimumOutput();
            BigInteger amount = output.getValue();
            BigInteger required = minimum.add(amount).subtract(BigInteger.ONE).divide(amount);
            factor = factor.max(required);
        }
        return factor;
    }

    public static List<ResourceAmount> scaleForMachines(List<ResourceAmount> resources, BigInteger k, int c) {
        FactoryLimits.requirePositiveQuantity(k);
        requireMachineCount(c, 1, FactoryLimits.MAX_MACHINES_PER_CELL);
        BigInteger multiplier = k.multiply(BigInteger.valueOf(c));
        Map<ResourceKey, BigInteger> merged = merge(resources);
        return merged.entrySet().stream()
                .map(entry -> new ResourceAmount(entry.getKey(), entry.getValue().multiply(multiplier)))
                .toList();
    }

    public static BigInteger energyPerTick(int machineCount) {
        requireMachineCount(machineCount, 0, FactoryLimits.MAX_TOTAL_MACHINES);
        return ENERGY_PER_MACHINE_TICK.multiply(BigInteger.valueOf(machineCount));
    }

    public static BigInteger energyPerBatch(int machineCount) {
        return energyPerTick(machineCount).multiply(BigInteger.valueOf(BATCH_TICKS));
    }

    private static Map<ResourceKey, BigInteger> merge(List<ResourceAmount> resources) {
        Objects.requireNonNull(resources, "resources");
        Map<ResourceKey, BigInteger> merged = new LinkedHashMap<>();
        for (ResourceAmount resource : resources) {
            Objects.requireNonNull(resource, "resource");
            BigInteger total = merged.getOrDefault(resource.resource(), BigInteger.ZERO).add(resource.amount());
            FactoryLimits.requirePositiveQuantity(total);
            merged.put(resource.resource(), total);
            if (merged.size() > FactoryLimits.MAX_RECIPE_RESOURCES) {
                throw new ModelValidationException(FactoryDiagnostic.Code.RESOURCE_LIMIT_EXCEEDED,
                        "A recipe exceeds " + FactoryLimits.MAX_RECIPE_RESOURCES + " resource kinds");
            }
        }
        return merged;
    }

    private static void requireBand(int band) {
        if (band < 1 || band > 10) {
            throw new ModelValidationException(FactoryDiagnostic.Code.INVALID_ENVIRONMENT,
                    "Environment bands must be between 1 and 10");
        }
    }

    private static void requireMachineCount(int count, int minimum, int maximum) {
        if (count < minimum || count > maximum) {
            throw new ModelValidationException(FactoryDiagnostic.Code.INVALID_MACHINE_COUNT,
                    "Machine count must be between " + minimum + " and " + maximum);
        }
    }

    public record Environment(int stabilityBand, int temperatureBand) {
        public Environment {
            requireBand(stabilityBand);
            requireBand(temperatureBand);
        }

        public int parallelism() {
            return FactoryRules.parallelism(stabilityBand, temperatureBand);
        }
    }
}
