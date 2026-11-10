package com.aquariverr.fluxod.block.entity;

public enum QuantumFeedbackMetric {
    STABILITY(1, 0, 10_000, 0, 10_000),
    TEMPERATURE(2, 0, 4_000_000, 29_315, 4_000_000);

    private final int id;
    private final int allowedMinimum;
    private final int allowedMaximum;
    private final int defaultMinimum;
    private final int defaultMaximum;

    QuantumFeedbackMetric(int id, int allowedMinimum, int allowedMaximum,
                          int defaultMinimum, int defaultMaximum) {
        this.id = id;
        this.allowedMinimum = allowedMinimum;
        this.allowedMaximum = allowedMaximum;
        this.defaultMinimum = defaultMinimum;
        this.defaultMaximum = defaultMaximum;
    }

    public int id() {
        return id;
    }

    public int allowedMinimum() {
        return allowedMinimum;
    }

    public int allowedMaximum() {
        return allowedMaximum;
    }

    public int defaultMinimum() {
        return defaultMinimum;
    }

    public int defaultMaximum() {
        return defaultMaximum;
    }

    public QuantumFeedbackCurve defaultCurve() {
        return QuantumFeedbackCurve.evenlySpaced(this, defaultMinimum, defaultMaximum, false);
    }

    public static QuantumFeedbackMetric byId(int id) {
        for (QuantumFeedbackMetric metric : values()) {
            if (metric.id == id) return metric;
        }
        return STABILITY;
    }
}
