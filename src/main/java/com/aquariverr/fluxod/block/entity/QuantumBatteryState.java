package com.aquariverr.fluxod.block.entity;

import java.math.BigInteger;
import java.util.Collection;
import java.util.List;

final class QuantumBatteryState {

    private static final float UNBREAKABLE_EXPLOSION_RESISTANCE = 3_600_000.0F;
    static final int MIN_CORE_LEVEL = 1;
    static final int MAX_CORE_LEVEL = 3;
    static final int MAX_PROGRESS_BASIS_POINTS = 10_000;
    static final BigInteger LEVEL_2_TOTAL_IO = BigInteger.valueOf(1_000_000_000L);
    static final BigInteger LEVEL_3_TOTAL_IO = BigInteger.valueOf(1_000_000_000_000_000L);

    static int highestFormedTier(List<? extends Collection<?>> interfacesByTier) {
        int formedTier = 0;
        for (Collection<?> interfaces : interfacesByTier) {
            if (interfaces.isEmpty()) break;
            formedTier++;
        }
        return formedTier;
    }

    static int animationTier(int structureTier, boolean suspended, boolean ready) {
        return structureTier > 0 && !suspended && ready ? structureTier : 0;
    }

    static int normalizeCoreLevel(int coreLevel) {
        return Math.clamp(coreLevel, MIN_CORE_LEVEL, MAX_CORE_LEVEL);
    }

    static int coreLevelForLoad(boolean hasSavedCoreLevel, int savedCoreLevel, int savedStructureTier) {
        return hasSavedCoreLevel
                ? normalizeCoreLevel(savedCoreLevel)
                : normalizeCoreLevel(savedStructureTier);
    }

    static boolean canUpgradeCore(int coreLevel, BigInteger totalIoEnergy) {
        int normalizedLevel = normalizeCoreLevel(coreLevel);
        return normalizedLevel < MAX_CORE_LEVEL
                && nonNegative(totalIoEnergy).compareTo(requiredTotalIoForNextLevel(normalizedLevel)) >= 0;
    }

    static int coreProgressBasisPoints(int coreLevel, BigInteger totalIoEnergy) {
        int normalizedLevel = normalizeCoreLevel(coreLevel);
        if (normalizedLevel >= MAX_CORE_LEVEL) return MAX_PROGRESS_BASIS_POINTS;

        BigInteger stageStart = normalizedLevel == MIN_CORE_LEVEL ? BigInteger.ZERO : LEVEL_2_TOTAL_IO;
        BigInteger stageEnd = requiredTotalIoForNextLevel(normalizedLevel);
        BigInteger stageRange = stageEnd.subtract(stageStart);
        BigInteger progress = nonNegative(totalIoEnergy).subtract(stageStart);
        if (progress.signum() <= 0) return 0;
        if (progress.compareTo(stageRange) >= 0) return MAX_PROGRESS_BASIS_POINTS;
        return progress.multiply(BigInteger.valueOf(MAX_PROGRESS_BASIS_POINTS))
                .divide(stageRange)
                .intValue();
    }

    static BigInteger accumulateTotalIo(BigInteger current, BigInteger inserted, BigInteger extracted) {
        BigInteger total = nonNegative(current)
                .add(nonNegative(inserted))
                .add(nonNegative(extracted));
        return QuantumEnergyCodec.clamp(total);
    }

    static double quenchRadius(BigInteger stored, double baseRadius, double radiusPerOrder, double maxRadius) {
        double base = Math.max(0.0, baseRadius);
        double max = Math.max(base, maxRadius);
        if (stored.signum() <= 0 || radiusPerOrder <= 0.0) return Math.min(base, max);

        int exponent = stored.toString().length() - 1;
        double radius = base + exponent * radiusPerOrder;
        return Math.min(radius, max);
    }

    /**
     * Simplified vanilla explosion pressure model: a block is destroyed when the remaining blast
     * pressure at its distance exceeds its explosion resistance.
     */
    static boolean shouldDestroyBlock(double radius, double distance, float resistance) {
        if (resistance >= UNBREAKABLE_EXPLOSION_RESISTANCE) return false;
        return radius * 2.0 - distance * (resistance + 0.3) > 0.0;
    }

    /**
     * Vanilla entity damage model: {@code (falloff^2 + falloff) / 2 * 7 * range + 1}, where
     * falloff is 1 at the center and 0 at the edge of the blast range.
     */
    static float explosionDamage(double range, double distance) {
        if (range <= 0.0) return 1.0F;
        double falloff = 1.0 - Math.max(0.0, distance) / range;
        if (falloff <= 0.0) return 1.0F;
        return (float) ((falloff * falloff + falloff) / 2.0 * 7.0 * range + 1.0);
    }

    private static BigInteger requiredTotalIoForNextLevel(int coreLevel) {
        return coreLevel == MIN_CORE_LEVEL ? LEVEL_2_TOTAL_IO : LEVEL_3_TOTAL_IO;
    }

    private static BigInteger nonNegative(BigInteger value) {
        return value.signum() > 0 ? value : BigInteger.ZERO;
    }

    static String formatStability(int basisPoints) {
        int clamped = Math.clamp(basisPoints, 0, QuantumStabilityGauge.MAX_BASIS_POINTS);
        int whole = clamped / 100;
        int fraction = clamped % 100;
        if (fraction == 0) return whole + "%";
        if (fraction % 10 == 0) return whole + "." + fraction / 10 + "%";
        return whole + "." + (fraction < 10 ? "0" : "") + fraction + "%";
    }

    private QuantumBatteryState() {}
}
