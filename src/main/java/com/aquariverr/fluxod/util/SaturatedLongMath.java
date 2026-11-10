package com.aquariverr.fluxod.util;

public final class SaturatedLongMath {

    public static long addNonNegative(long first, long second) {
        if (first < 0 || second < 0 || first > Long.MAX_VALUE - second) {
            return Long.MAX_VALUE;
        }
        return first + second;
    }

    public static long magnitudeOfNegative(long value) {
        if (value >= 0) return 0;
        return value == Long.MIN_VALUE ? Long.MAX_VALUE : -value;
    }

    private SaturatedLongMath() {}
}
