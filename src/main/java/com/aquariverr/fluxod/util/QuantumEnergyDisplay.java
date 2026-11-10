package com.aquariverr.fluxod.util;

import java.math.BigInteger;

public record QuantumEnergyDisplay(int significand, int exponent, int logBasisPoints) {

    public static final QuantumEnergyDisplay ZERO = new QuantumEnergyDisplay(0, 0, 0);
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    private static final double LONG_MAX_LOG = Math.log1p((double) Long.MAX_VALUE);

    public QuantumEnergyDisplay {
        significand = Math.max(0, significand);
        exponent = Math.max(0, exponent);
        logBasisPoints = Math.clamp(logBasisPoints, 0, 10_000);
    }

    public static QuantumEnergyDisplay from(BigInteger value) {
        BigInteger energy = value.max(BigInteger.ZERO);
        if (energy.signum() == 0) return ZERO;

        String digits = energy.toString();
        int exponent;
        int significand;
        if (digits.length() <= 6) {
            exponent = 0;
            significand = Integer.parseInt(digits);
        } else {
            exponent = digits.length() - 1;
            significand = Integer.parseInt(digits.substring(0, Math.min(4, digits.length())));
        }

        return new QuantumEnergyDisplay(significand, exponent, logBasisPoints(energy));
    }

    public static int logBasisPoints(BigInteger value) {
        BigInteger energy = value.max(BigInteger.ZERO);
        if (energy.signum() == 0) return 0;
        if (energy.compareTo(LONG_MAX) >= 0) return 10_000;
        double normalized = Math.log1p((double) energy.longValueExact()) / LONG_MAX_LOG;
        return (int) Math.round(normalized * 10_000.0);
    }

    public String compact() {
        if (exponent < 6) return Integer.toString(significand);
        String digits = String.format(java.util.Locale.ROOT, "%04d", significand);
        return digits.charAt(0) + "." + digits.substring(1) + "E" + exponent;
    }

    public double normalizedLogScale() {
        return logBasisPoints / 10_000.0;
    }
}
