package com.aquariverr.fluxod.util;

public final class DeviceTickSampler {

    public static final int WINDOW_TICKS = 20;

    private long currentTickNanos;
    private long windowNanos;
    private int windowTicks;
    private int averageTickMicro = FluxDeviceTiming.UNAVAILABLE;

    public void beginTick() {
        currentTickNanos = 0;
    }

    public void recordNanos(long nanos) {
        if (nanos < 0) return;
        currentTickNanos = SaturatedLongMath.addNonNegative(currentTickNanos, nanos);
    }

    public void endTick() {
        windowNanos = SaturatedLongMath.addNonNegative(windowNanos, currentTickNanos);
        currentTickNanos = 0;
        windowTicks++;
        if (windowTicks < WINDOW_TICKS) return;

        long averageMicro = windowNanos / (WINDOW_TICKS * 1_000L);
        averageTickMicro = (int) Math.min(averageMicro, Integer.MAX_VALUE);
        windowNanos = 0;
        windowTicks = 0;
    }

    public void reset() {
        currentTickNanos = 0;
        windowNanos = 0;
        windowTicks = 0;
        averageTickMicro = FluxDeviceTiming.UNAVAILABLE;
    }

    public int averageTickMicro() {
        return averageTickMicro;
    }
}
