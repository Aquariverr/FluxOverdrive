package com.aquariverr.fluxod.block.entity;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuantumBatteryCoolingModelTest {

    @Test
    void coolsToEachConfiguredTemperatureAnchor() {
        for (long target : List.of(273_000L, 263_000L, 253_000L)) {
            TestSource source = new TestSource(100_000L).add("coolant", target, 1_000_000L);
            QuantumBatteryCoolingModel.Result result = settle(293_000L, 0L, List.of(source));

            assertEquals(target, result.temperatureMilliKelvin());
            assertEquals(293_000L - target, result.removedMilliKelvin());
            assertEquals(target, result.effectiveTargetMilliKelvin());
            assertTrue(result.active());
        }
    }

    @Test
    void neverCoolsBelowTheMediumTarget() {
        TestSource source = new TestSource(80_000L).add("ice", 273_000L, 1_000_000L);

        QuantumBatteryCoolingModel.Result result = settle(270_000L, 5_000L, List.of(source));

        assertEquals(273_000L, result.temperatureMilliKelvin());
        assertEquals(2_000L, result.removedMilliKelvin());
    }

    @Test
    void reportsResourceExhaustionWithoutInventingCooling() {
        TestSource source = new TestSource(80_000L).add("ice", 273_000L, 1_500L);

        QuantumBatteryCoolingModel.Result result = settle(293_000L, 5_000L, List.of(source));

        assertEquals(296_500L, result.temperatureMilliKelvin());
        assertEquals(1_500L, result.removedMilliKelvin());
        assertEquals(0L, source.remaining("ice"));
    }

    @Test
    void combinesPluginRatesAndUsesWarmerMediaFirst() {
        TestSource ice = new TestSource(10_000L).add("ice", 273_000L, 1_000_000L);
        TestSource blueIce = new TestSource(10_000L).add("blue", 253_000L, 1_000_000L);

        QuantumBatteryCoolingModel.Result result = settle(293_000L, 0L, List.of(ice, blueIce));

        assertEquals(273_000L, result.temperatureMilliKelvin());
        assertEquals(990_000L, ice.remaining("ice"));
        assertEquals(990_000L, blueIce.remaining("blue"));
    }

    @Test
    void rotatesEqualTargetSourcesBetweenTicks() {
        TestSource first = new TestSource(5_000L).add("first", 273_000L, 1_000_000L);
        TestSource second = new TestSource(5_000L).add("second", 273_000L, 1_000_000L);

        QuantumBatteryCoolingModel.Result firstTick = settle(278_000L, 0L, List.of(first, second), 0);
        QuantumBatteryCoolingModel.Result secondTick = settle(278_000L, 0L, List.of(first, second),
                firstTick.nextRoundRobinCursor());

        assertEquals(995_000L, first.remaining("first"));
        assertEquals(995_000L, second.remaining("second"));
        assertEquals(0, secondTick.nextRoundRobinCursor());
    }

    @Test
    void skipsEmptySourcesWithoutBiasingRoundRobinOrder() {
        TestSource first = new TestSource(5_000L).add("first", 273_000L, 1_000_000L);
        TestSource empty = new TestSource(5_000L);
        TestSource third = new TestSource(5_000L).add("third", 273_000L, 1_000_000L);
        List<TestSource> sources = List.of(first, empty, third);

        int cursor = settle(278_000L, 0L, sources, 0).nextRoundRobinCursor();
        cursor = settle(278_000L, 0L, sources, cursor).nextRoundRobinCursor();
        settle(278_000L, 0L, sources, cursor);

        assertEquals(990_000L, first.remaining("first"));
        assertEquals(995_000L, third.remaining("third"));
    }

    @Test
    void keepsCursorWhenNoCoolingIsConsumed() {
        TestSource source = new TestSource(5_000L).add("ice", 273_000L, 1_000_000L);

        QuantumBatteryCoolingModel.Result result = settle(270_000L, 0L, List.of(source), 0);

        assertEquals(0, result.nextRoundRobinCursor());
        assertEquals(1_000_000L, source.remaining("ice"));
    }

    @Test
    void heatingIsAppliedBeforeCooling() {
        TestSource source = new TestSource(80_000L).add("ice", 273_000L, 1_000_000L);

        assertEquals(273_000L, settle(273_000L, 5_000L, List.of(source)).temperatureMilliKelvin());
        assertEquals(995_000L, source.remaining("ice"));
    }

    @Test
    void emptySourcesOnlyApplyHeating() {
        QuantumBatteryCoolingModel.Result result = settle(293_150L, 125_000L, List.of());

        assertEquals(418_150L, result.temperatureMilliKelvin());
        assertEquals(QuantumBatteryCoolingModel.NO_TARGET, result.effectiveTargetMilliKelvin());
        assertFalse(result.active());
    }

    private static QuantumBatteryCoolingModel.Result settle(
            long temperature, long heating, List<TestSource> sources) {
        return settle(temperature, heating, sources, 0);
    }

    private static QuantumBatteryCoolingModel.Result settle(
            long temperature, long heating, List<TestSource> sources, int cursor) {
        return QuantumBatteryCoolingModel.settle(temperature, heating, 40_000_000L, sources, cursor);
    }

    private static final class TestSource implements QuantumBatteryCoolingSource {
        private final long rate;
        private final Map<String, MediumState> media = new LinkedHashMap<>();

        private TestSource(long rate) {
            this.rate = rate;
        }

        private TestSource add(String id, long target, long available) {
            media.put(id, new MediumState(target, available));
            return this;
        }

        private long remaining(String id) {
            return media.get(id).available;
        }

        @Override
        public List<CoolingMedium> getCoolingMedia() {
            List<CoolingMedium> result = new ArrayList<>();
            media.forEach((id, state) -> result.add(new CoolingMedium(id, state.target, state.available)));
            return result;
        }

        @Override
        public long getMaxCoolingPerTickMilliKelvin() {
            return rate;
        }

        @Override
        public long consumeCooling(String channelId, long requestedMilliKelvin) {
            MediumState state = media.get(channelId);
            long consumed = Math.min(requestedMilliKelvin, state.available);
            state.available -= consumed;
            return consumed;
        }
    }

    private static final class MediumState {
        private final long target;
        private long available;

        private MediumState(long target, long available) {
            this.target = target;
            this.available = available;
        }
    }
}
