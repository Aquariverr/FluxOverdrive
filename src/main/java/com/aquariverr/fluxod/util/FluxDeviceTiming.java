package com.aquariverr.fluxod.util;

public interface FluxDeviceTiming {

    int UNAVAILABLE = -1;
    String AVERAGE_TICK_MICRO_TAG = "flux_overdrive:average_tick_micro";

    int fluxod$getAverageTickMicro();

    default void fluxod$beginTimingTick() {}

    default void fluxod$recordTimingNanos(long nanos) {}

    default void fluxod$endTimingTick() {}

    default void fluxod$resetTiming() {}
}
