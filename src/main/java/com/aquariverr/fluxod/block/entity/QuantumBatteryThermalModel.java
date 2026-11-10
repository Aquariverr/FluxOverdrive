package com.aquariverr.fluxod.block.entity;

import java.math.BigInteger;

public final class QuantumBatteryThermalModel {

    public static final double ROOM_TEMPERATURE_K = 293.15;
    public static final double MAX_TEMPERATURE_K = 40_000.0;
    public static final long MULTIPLIER_SCALE = 1_000_000L;
    private static final BigInteger BIG_MULTIPLIER_SCALE = BigInteger.valueOf(MULTIPLIER_SCALE);

    public static double heatingPerSecond(int tier) {
        return switch (tier) {
            case 1 -> 100.0;
            case 2 -> 500.0;
            case 3 -> 2_500.0;
            default -> 0.0;
        };
    }

    public static double heatingPerTick(int tier) {
        return heatingPerSecond(tier) / 20.0;
    }

    public static double clampTemperature(double kelvin) {
        if (!Double.isFinite(kelvin)) return ROOM_TEMPERATURE_K;
        return Math.max(0.0, Math.min(MAX_TEMPERATURE_K, kelvin));
    }

    public static double thermalLoadPercent(double kelvin) {
        double temperature = clampTemperature(kelvin);
        if (temperature <= ROOM_TEMPERATURE_K) return 0.0;
        return (temperature - ROOM_TEMPERATURE_K)
                / (MAX_TEMPERATURE_K - ROOM_TEMPERATURE_K) * 100.0;
    }

    public static double constraintEnergyMultiplier(double kelvin) {
        double temperature = clampTemperature(kelvin);
        if (temperature <= ROOM_TEMPERATURE_K) {
            return smoothstep(temperature / ROOM_TEMPERATURE_K);
        }
        double normalized = (temperature - ROOM_TEMPERATURE_K)
                / (MAX_TEMPERATURE_K - ROOM_TEMPERATURE_K);
        return 1.0 + smoothstep(normalized);
    }

    public static long multiplierScaled(double kelvin) {
        long scaled = Math.round(constraintEnergyMultiplier(kelvin) * MULTIPLIER_SCALE);
        if (kelvin > 0.0 && scaled == 0L) return 1L;
        return Math.clamp(scaled, 0L, MULTIPLIER_SCALE * 2L);
    }

    public static BigInteger scaleConstraintCost(BigInteger baseCost, double kelvin) {
        if (baseCost.signum() <= 0) return BigInteger.ZERO;
        long multiplier = multiplierScaled(kelvin);
        if (multiplier == 0L) return BigInteger.ZERO;
        return baseCost.multiply(BigInteger.valueOf(multiplier))
                .add(BIG_MULTIPLIER_SCALE.subtract(BigInteger.ONE))
                .divide(BIG_MULTIPLIER_SCALE);
    }

    private static double smoothstep(double value) {
        double x = Math.max(0.0, Math.min(1.0, value));
        return x * x * (3.0 - 2.0 * x);
    }

    private QuantumBatteryThermalModel() {}
}
