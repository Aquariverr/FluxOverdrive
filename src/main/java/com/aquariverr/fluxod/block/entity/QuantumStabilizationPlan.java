package com.aquariverr.fluxod.block.entity;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class QuantumStabilizationPlan {

    static Optional<List<Long>> createForRings(List<? extends List<Long>> ringStoredEnergy,
                                               List<Long> ringCosts) {
        if (ringStoredEnergy.size() != ringCosts.size()) {
            throw new IllegalArgumentException("Ring energy and cost counts must match");
        }

        BigInteger requiredEnergy = BigInteger.ZERO;
        List<Long> combinedEnergy = new ArrayList<>();
        for (int ring = 0; ring < ringStoredEnergy.size(); ring++) {
            List<Long> storedEnergy = ringStoredEnergy.get(ring);
            long cost = ringCosts.get(ring);
            if (storedEnergy.isEmpty()) return Optional.empty();
            if (cost < 0) throw new IllegalArgumentException("Stabilization energy cannot be negative");
            combinedEnergy.addAll(storedEnergy);
            requiredEnergy = requiredEnergy.add(BigInteger.valueOf(cost));
        }
        return create(combinedEnergy, requiredEnergy);
    }

    static Optional<List<Long>> create(List<Long> storedEnergy, BigInteger requiredEnergy) {
        if (requiredEnergy.signum() < 0) {
            throw new IllegalArgumentException("Stabilization energy cannot be negative");
        }

        long[] stored = new long[storedEnergy.size()];
        for (int index = 0; index < stored.length; index++) {
            stored[index] = storedEnergy.get(index);
        }
        return create(stored, requiredEnergy).map(QuantumStabilizationPlan::asList);
    }

    static Optional<long[]> create(long[] storedEnergy, BigInteger requiredEnergy) {
        if (requiredEnergy.signum() < 0) {
            throw new IllegalArgumentException("Stabilization energy cannot be negative");
        }
        if (requiredEnergy.bitLength() <= 63) {
            return create(storedEnergy, requiredEnergy.longValue());
        }

        BigInteger remaining = requiredEnergy;
        long[] extractions = new long[storedEnergy.length];
        for (int index = 0; index < storedEnergy.length; index++) {
            long available = Math.max(0, storedEnergy[index]);
            BigInteger extracted = remaining.min(BigInteger.valueOf(available));
            extractions[index] = extracted.longValueExact();
            remaining = remaining.subtract(extracted);
        }
        return remaining.signum() == 0 ? Optional.of(extractions) : Optional.empty();
    }

    static Optional<long[]> create(long[] storedEnergy, long requiredEnergy) {
        if (requiredEnergy < 0) {
            throw new IllegalArgumentException("Stabilization energy cannot be negative");
        }

        long remaining = requiredEnergy;
        long[] extractions = new long[storedEnergy.length];
        for (int index = 0; index < storedEnergy.length; index++) {
            long available = Math.max(0, storedEnergy[index]);
            long extracted = Math.min(remaining, available);
            extractions[index] = extracted;
            remaining -= extracted;
        }
        return remaining == 0 ? Optional.of(extractions) : Optional.empty();
    }

    private static List<Long> asList(long[] values) {
        List<Long> result = new ArrayList<>(values.length);
        for (long value : values) result.add(value);
        return List.copyOf(result);
    }

    private QuantumStabilizationPlan() {}
}
