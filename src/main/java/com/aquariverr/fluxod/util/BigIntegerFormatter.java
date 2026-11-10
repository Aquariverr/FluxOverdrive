package com.aquariverr.fluxod.util;

import java.math.BigInteger;

public final class BigIntegerFormatter {

    public static String compact(BigInteger value) {
        String digits = value.max(BigInteger.ZERO).toString();
        if (digits.length() <= 6) return digits;

        String fraction = digits.substring(1, Math.min(4, digits.length()));
        return digits.charAt(0) + "." + fraction + "E" + (digits.length() - 1);
    }

    public static String formatStorage(NetworkEnergyTotals totals, String suffix) {
        String value = switch (totals.capacityStatus()) {
            case FINITE -> compact(totals.stored()) + " / " + compact(totals.finiteCapacity());
            case UNLIMITED -> compact(totals.stored()) + " / ∞";
            case UNKNOWN -> compact(totals.stored());
        };
        return suffix == null || suffix.isBlank() ? value : value + " " + suffix;
    }

    private BigIntegerFormatter() {}
}
