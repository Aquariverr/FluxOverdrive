package com.aquariverr.fluxod.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeviceTickSamplerTest {

    @Test
    void publishesOnlyAfterACompleteWindow() {
        DeviceTickSampler sampler = new DeviceTickSampler();

        sampleTicks(sampler, DeviceTickSampler.WINDOW_TICKS - 1, 1_000);
        assertEquals(FluxDeviceTiming.UNAVAILABLE, sampler.averageTickMicro());

        sampleTick(sampler, 1_000);
        assertEquals(1, sampler.averageTickMicro());
    }

    @Test
    void addsBothCallbackMeasurementsWithinEachTick() {
        DeviceTickSampler sampler = new DeviceTickSampler();

        for (int tick = 0; tick < DeviceTickSampler.WINDOW_TICKS; tick++) {
            sampler.beginTick();
            sampler.recordNanos(400);
            sampler.recordNanos(600);
            sampler.endTick();
        }

        assertEquals(1, sampler.averageTickMicro());
    }

    @Test
    void keepsPublishedValueUntilTheNextWindowCompletes() {
        DeviceTickSampler sampler = new DeviceTickSampler();
        sampleTicks(sampler, DeviceTickSampler.WINDOW_TICKS, 1_000);

        sampleTicks(sampler, DeviceTickSampler.WINDOW_TICKS - 1, 2_000);
        assertEquals(1, sampler.averageTickMicro());

        sampleTick(sampler, 2_000);
        assertEquals(2, sampler.averageTickMicro());
    }

    @Test
    void publishesZeroAndIgnoresNegativeMeasurements() {
        DeviceTickSampler zeroSampler = new DeviceTickSampler();
        sampleTicks(zeroSampler, DeviceTickSampler.WINDOW_TICKS, 0);
        assertEquals(0, zeroSampler.averageTickMicro());

        DeviceTickSampler negativeSampler = new DeviceTickSampler();
        for (int tick = 0; tick < DeviceTickSampler.WINDOW_TICKS; tick++) {
            negativeSampler.beginTick();
            negativeSampler.recordNanos(-1);
            negativeSampler.recordNanos(1_000);
            negativeSampler.endTick();
        }
        assertEquals(1, negativeSampler.averageTickMicro());
    }

    @Test
    void saturatesAtTheIntegerMicrosecondLimit() {
        DeviceTickSampler sampler = new DeviceTickSampler();
        sampler.beginTick();
        sampler.recordNanos(Long.MAX_VALUE);
        sampler.recordNanos(Long.MAX_VALUE);
        sampler.endTick();
        sampleTicks(sampler, DeviceTickSampler.WINDOW_TICKS - 1, 0);

        assertEquals(Integer.MAX_VALUE, sampler.averageTickMicro());
    }

    @Test
    void resetClearsPublishedAndPartialWindows() {
        DeviceTickSampler sampler = new DeviceTickSampler();
        sampleTicks(sampler, DeviceTickSampler.WINDOW_TICKS, 1_000);
        sampleTicks(sampler, 7, 50_000);

        sampler.reset();
        assertEquals(FluxDeviceTiming.UNAVAILABLE, sampler.averageTickMicro());

        sampleTicks(sampler, DeviceTickSampler.WINDOW_TICKS, 2_000);
        assertEquals(2, sampler.averageTickMicro());
    }

    @Test
    void samplersKeepIndependentWindows() {
        DeviceTickSampler first = new DeviceTickSampler();
        DeviceTickSampler second = new DeviceTickSampler();

        sampleTicks(first, DeviceTickSampler.WINDOW_TICKS, 1_000);
        sampleTicks(second, DeviceTickSampler.WINDOW_TICKS - 1, 2_000);

        assertEquals(1, first.averageTickMicro());
        assertEquals(FluxDeviceTiming.UNAVAILABLE, second.averageTickMicro());
    }

    private static void sampleTicks(DeviceTickSampler sampler, int ticks, long nanos) {
        for (int tick = 0; tick < ticks; tick++) {
            sampleTick(sampler, nanos);
        }
    }

    private static void sampleTick(DeviceTickSampler sampler, long nanos) {
        sampler.beginTick();
        sampler.recordNanos(nanos);
        sampler.endTick();
    }
}
