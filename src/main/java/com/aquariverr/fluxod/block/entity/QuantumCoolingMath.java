package com.aquariverr.fluxod.block.entity;

final class QuantumCoolingMath {

    static long saturatingAdd(long left, long right) {
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }

    static long saturatingMultiply(long left, long right) {
        if (left <= 0L || right <= 0L) return 0L;
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }

    static long ceilDiv(long value, long divisor) {
        if (value <= 0L || divisor <= 0L) return 0L;
        return 1L + (value - 1L) / divisor;
    }

    private QuantumCoolingMath() {}
}
