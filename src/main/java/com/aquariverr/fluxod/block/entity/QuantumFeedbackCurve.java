package com.aquariverr.fluxod.block.entity;

import java.util.ArrayList;
import java.util.List;

public record QuantumFeedbackCurve(QuantumFeedbackMetric metric, List<Point> points) {

    public static final int MIN_POINTS = 2;
    public static final int MAX_POINTS = 32;
    public static final int MIN_SIGNAL = 0;
    public static final int MAX_SIGNAL = 15;

    public QuantumFeedbackCurve {
        if (metric == null) throw new IllegalArgumentException("Feedback metric must not be null");
        points = List.copyOf(points);
        validate(metric, points);
    }

    public int evaluate(int value) {
        int signal = (int) Math.round(interpolatedSignal(value));
        return Math.clamp(signal, MIN_SIGNAL, MAX_SIGNAL);
    }

    public double interpolatedSignal(int value) {
        Point first = points.getFirst();
        if (value <= first.threshold()) return first.signal();
        for (int index = 1; index < points.size(); index++) {
            Point left = points.get(index - 1);
            Point right = points.get(index);
            if (value > right.threshold()) continue;
            double progress = (value - left.threshold())
                    / (double) (right.threshold() - left.threshold());
            return left.signal() + (right.signal() - left.signal()) * progress;
        }
        return points.getLast().signal();
    }

    public int minimumThreshold() {
        return points.getFirst().threshold();
    }

    public int maximumThreshold() {
        return points.getLast().threshold();
    }

    public static QuantumFeedbackCurve evenlySpaced(QuantumFeedbackMetric metric,
                                                      int minimum, int maximum,
                                                      boolean descending) {
        if (maximum <= minimum) throw new IllegalArgumentException("Feedback range must be positive");
        int range = maximum - minimum;
        int segments = Math.min(MAX_SIGNAL, range);
        List<Point> points = new ArrayList<>(segments + 1);
        for (int index = 0; index <= segments; index++) {
            int threshold = minimum + (int) ((range * (long) index + segments / 2L) / segments);
            int ascendingSignal = (int) ((MAX_SIGNAL * (long) index + segments / 2L) / segments);
            points.add(new Point(threshold,
                    descending ? MAX_SIGNAL - ascendingSignal : ascendingSignal));
        }
        return new QuantumFeedbackCurve(metric, points);
    }

    private static void validate(QuantumFeedbackMetric metric, List<Point> points) {
        if (points.size() < MIN_POINTS || points.size() > MAX_POINTS) {
            throw new IllegalArgumentException("Feedback curves require 2-32 points");
        }
        int previousThreshold = Integer.MIN_VALUE;
        for (Point point : points) {
            if (point.threshold() < metric.allowedMinimum()
                    || point.threshold() > metric.allowedMaximum()) {
                throw new IllegalArgumentException("Feedback threshold is outside the metric range");
            }
            if (point.threshold() <= previousThreshold) {
                throw new IllegalArgumentException("Feedback thresholds must be strictly increasing");
            }
            if (point.signal() < MIN_SIGNAL || point.signal() > MAX_SIGNAL) {
                throw new IllegalArgumentException("Feedback signal must be between 0 and 15");
            }
            previousThreshold = point.threshold();
        }
        if (metric == QuantumFeedbackMetric.STABILITY
                && (points.getFirst().threshold() != metric.allowedMinimum()
                || points.getLast().threshold() != metric.allowedMaximum())) {
            throw new IllegalArgumentException("Stability curves must span 0-100 percent");
        }
    }

    public record Point(int threshold, int signal) {}
}
