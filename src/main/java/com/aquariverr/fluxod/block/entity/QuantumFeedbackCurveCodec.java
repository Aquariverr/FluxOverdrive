package com.aquariverr.fluxod.block.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

public final class QuantumFeedbackCurveCodec {

    private static final String POINTS_TAG = "Points";
    private static final String THRESHOLD_TAG = "Threshold";
    private static final String SIGNAL_TAG = "Signal";

    public static CompoundTag save(QuantumFeedbackCurve curve) {
        CompoundTag tag = new CompoundTag();
        ListTag entries = new ListTag();
        for (QuantumFeedbackCurve.Point point : curve.points()) {
            CompoundTag entry = new CompoundTag();
            entry.putInt(THRESHOLD_TAG, point.threshold());
            entry.putInt(SIGNAL_TAG, point.signal());
            entries.add(entry);
        }
        tag.put(POINTS_TAG, entries);
        return tag;
    }

    public static QuantumFeedbackCurve load(QuantumFeedbackMetric metric, CompoundTag tag) {
        ListTag entries = tag.getList(POINTS_TAG, Tag.TAG_COMPOUND);
        if (entries.size() < QuantumFeedbackCurve.MIN_POINTS
                || entries.size() > QuantumFeedbackCurve.MAX_POINTS) {
            throw new IllegalArgumentException("Feedback curves require 2-32 points");
        }
        List<QuantumFeedbackCurve.Point> points = new ArrayList<>(entries.size());
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag entry = entries.getCompound(index);
            points.add(new QuantumFeedbackCurve.Point(
                    entry.getInt(THRESHOLD_TAG), entry.getInt(SIGNAL_TAG)));
        }
        return new QuantumFeedbackCurve(metric, points);
    }

    public static QuantumFeedbackCurve loadOrDefault(QuantumFeedbackMetric metric, CompoundTag tag) {
        try {
            return load(metric, tag);
        } catch (RuntimeException ignored) {
            return metric.defaultCurve();
        }
    }

    private QuantumFeedbackCurveCodec() {}
}
