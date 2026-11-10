package com.aquariverr.fluxod.block.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class QuantumBatteryCoolingModel {

    public static final long MILLI_KELVIN_PER_KELVIN = 1_000L;
    public static final long NO_TARGET = -1L;

    public static Result settle(
            long temperatureMilliKelvin,
            long heatingMilliKelvin,
            long maximumTemperatureMilliKelvin,
            List<? extends QuantumBatteryCoolingSource> sources,
            int roundRobinCursor) {
        long maximum = Math.max(0L, maximumTemperatureMilliKelvin);
        long temperature = Math.clamp(saturatingAdd(
                Math.max(0L, temperatureMilliKelvin), Math.max(0L, heatingMilliKelvin)), 0L, maximum);
        if (sources.isEmpty()) {
            return new Result(temperature, 0L, NO_TARGET, false, 0);
        }

        int sourceCount = sources.size();
        int cursor = Math.floorMod(roundRobinCursor, sourceCount);
        long[] sourceBudgets = new long[sourceCount];
        List<MediumRef> media = new ArrayList<>();
        long effectiveTarget = NO_TARGET;

        for (int sourceIndex = 0; sourceIndex < sourceCount; sourceIndex++) {
            QuantumBatteryCoolingSource source = sources.get(sourceIndex);
            sourceBudgets[sourceIndex] = Math.max(0L, source.getMaxCoolingPerTickMilliKelvin());
            if (sourceBudgets[sourceIndex] == 0L) continue;
            for (QuantumBatteryCoolingSource.CoolingMedium medium : source.getCoolingMedia()) {
                if (medium.availableCoolingMilliKelvin() == 0L) continue;
                media.add(new MediumRef(sourceIndex, medium));
                effectiveTarget = effectiveTarget == NO_TARGET
                        ? medium.targetMilliKelvin()
                        : Math.min(effectiveTarget, medium.targetMilliKelvin());
            }
        }

        media.sort(Comparator
                .comparingLong((MediumRef ref) -> ref.medium().targetMilliKelvin()).reversed()
                .thenComparingInt(ref -> Math.floorMod(ref.sourceIndex() - cursor, sourceCount))
                .thenComparing(ref -> ref.medium().channelId()));

        long removed = 0L;
        int firstConsumerIndex = -1;
        for (MediumRef ref : media) {
            int sourceIndex = ref.sourceIndex();
            long sourceBudget = sourceBudgets[sourceIndex];
            long needed = temperature - ref.medium().targetMilliKelvin();
            if (sourceBudget <= 0L || needed <= 0L) continue;

            long requested = Math.min(needed,
                    Math.min(sourceBudget, ref.medium().availableCoolingMilliKelvin()));
            long consumed = sources.get(sourceIndex).consumeCooling(ref.medium().channelId(), requested);
            consumed = Math.clamp(consumed, 0L, requested);
            if (consumed > 0L && firstConsumerIndex < 0) firstConsumerIndex = sourceIndex;
            temperature -= consumed;
            removed += consumed;
            sourceBudgets[sourceIndex] -= consumed;
        }

        int nextCursor = firstConsumerIndex < 0
                ? cursor
                : Math.floorMod(firstConsumerIndex + 1, sourceCount);
        return new Result(temperature, removed, effectiveTarget, removed > 0L, nextCursor);
    }

    public static long milliKelvin(double kelvin) {
        if (!Double.isFinite(kelvin) || kelvin <= 0.0) return 0L;
        double scaled = kelvin * MILLI_KELVIN_PER_KELVIN;
        return scaled >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.round(scaled);
    }

    public static double kelvin(long milliKelvin) {
        return Math.max(0L, milliKelvin) / (double) MILLI_KELVIN_PER_KELVIN;
    }

    private static long saturatingAdd(long left, long right) {
        if (Long.MAX_VALUE - left < right) return Long.MAX_VALUE;
        return left + right;
    }

    public record Result(
            long temperatureMilliKelvin,
            long removedMilliKelvin,
            long effectiveTargetMilliKelvin,
            boolean active,
            int nextRoundRobinCursor) {}

    private record MediumRef(int sourceIndex, QuantumBatteryCoolingSource.CoolingMedium medium) {}

    private QuantumBatteryCoolingModel() {}
}
