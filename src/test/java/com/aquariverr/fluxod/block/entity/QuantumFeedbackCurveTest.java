package com.aquariverr.fluxod.block.entity;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuantumFeedbackCurveTest {

    @Test
    void defaultStabilityCurveSpansEveryRedstoneLevel() {
        QuantumFeedbackCurve curve = QuantumFeedbackMetric.STABILITY.defaultCurve();

        assertEquals(16, curve.points().size());
        assertEquals(0, curve.minimumThreshold());
        assertEquals(10_000, curve.maximumThreshold());
        assertEquals(0, curve.evaluate(0));
        assertEquals(7, curve.evaluate(4_999));
        assertEquals(8, curve.evaluate(5_000));
        assertEquals(15, curve.evaluate(10_000));
    }

    @Test
    void interpolatesArbitraryNonMonotonicSegments() {
        QuantumFeedbackCurve curve = new QuantumFeedbackCurve(QuantumFeedbackMetric.STABILITY, List.of(
                new QuantumFeedbackCurve.Point(0, 15),
                new QuantumFeedbackCurve.Point(2_500, 2),
                new QuantumFeedbackCurve.Point(5_000, 12),
                new QuantumFeedbackCurve.Point(7_500, 0),
                new QuantumFeedbackCurve.Point(10_000, 7)));

        assertEquals(15, curve.evaluate(-1));
        assertEquals(9, curve.evaluate(1_250));
        assertEquals(2, curve.evaluate(2_499));
        assertEquals(2, curve.evaluate(2_500));
        assertEquals(7, curve.evaluate(3_750));
        assertEquals(6, curve.evaluate(6_249));
        assertEquals(4, curve.evaluate(8_750));
        assertEquals(7, curve.evaluate(10_001));
    }

    @Test
    void exposesFractionalInterpolationWithoutChangingIntegerEvaluation() {
        QuantumFeedbackCurve curve = new QuantumFeedbackCurve(QuantumFeedbackMetric.STABILITY, List.of(
                new QuantumFeedbackCurve.Point(0, 0),
                new QuantumFeedbackCurve.Point(10_000, 15)));

        assertEquals(1.5, curve.interpolatedSignal(1_000), 1.0E-9);
        assertEquals(2, curve.evaluate(1_000));
    }

    @Test
    void continuousInterpolationClampsToCurveEndpoints() {
        QuantumFeedbackCurve curve = new QuantumFeedbackCurve(QuantumFeedbackMetric.TEMPERATURE, List.of(
                new QuantumFeedbackCurve.Point(30_000, 1),
                new QuantumFeedbackCurve.Point(50_000, 15),
                new QuantumFeedbackCurve.Point(100_000, 4)));

        assertEquals(1.0, curve.interpolatedSignal(0), 1.0E-9);
        assertEquals(4.0, curve.interpolatedSignal(4_000_000), 1.0E-9);
    }

    @Test
    void continuousInterpolationFollowsDescendingAndNonMonotonicSegments() {
        QuantumFeedbackCurve curve = new QuantumFeedbackCurve(QuantumFeedbackMetric.STABILITY, List.of(
                new QuantumFeedbackCurve.Point(0, 15),
                new QuantumFeedbackCurve.Point(2_500, 2),
                new QuantumFeedbackCurve.Point(5_000, 12),
                new QuantumFeedbackCurve.Point(10_000, 0)));

        assertEquals(8.5, curve.interpolatedSignal(1_250), 1.0E-9);
        assertEquals(4.5, curve.interpolatedSignal(3_125), 1.0E-9);
        assertEquals(9.0, curve.interpolatedSignal(6_250), 1.0E-9);
    }

    @Test
    void temperatureEndpointsDefineTheClampedDomain() {
        QuantumFeedbackCurve curve = new QuantumFeedbackCurve(QuantumFeedbackMetric.TEMPERATURE, List.of(
                new QuantumFeedbackCurve.Point(30_000, 1),
                new QuantumFeedbackCurve.Point(50_000, 15),
                new QuantumFeedbackCurve.Point(100_000, 4)));

        assertEquals(1, curve.evaluate(0));
        assertEquals(8, curve.evaluate(40_000));
        assertEquals(15, curve.evaluate(49_999));
        assertEquals(15, curve.evaluate(50_000));
        assertEquals(10, curve.evaluate(75_000));
        assertEquals(4, curve.evaluate(4_000_000));
    }

    @Test
    void rejectsMalformedAndUnboundedCurves() {
        assertThrows(IllegalArgumentException.class, () -> new QuantumFeedbackCurve(
                QuantumFeedbackMetric.STABILITY, List.of(new QuantumFeedbackCurve.Point(0, 0))));
        assertThrows(IllegalArgumentException.class, () -> new QuantumFeedbackCurve(
                QuantumFeedbackMetric.STABILITY, List.of(
                new QuantumFeedbackCurve.Point(0, 0),
                new QuantumFeedbackCurve.Point(0, 1),
                new QuantumFeedbackCurve.Point(10_000, 15))));
        assertThrows(IllegalArgumentException.class, () -> new QuantumFeedbackCurve(
                QuantumFeedbackMetric.STABILITY, List.of(
                new QuantumFeedbackCurve.Point(1, 0),
                new QuantumFeedbackCurve.Point(10_000, 15))));
        assertThrows(IllegalArgumentException.class, () -> new QuantumFeedbackCurve(
                QuantumFeedbackMetric.TEMPERATURE, List.of(
                new QuantumFeedbackCurve.Point(0, -1),
                new QuantumFeedbackCurve.Point(4_000_000, 15))));

        List<QuantumFeedbackCurve.Point> tooMany = new ArrayList<>();
        for (int threshold = 0; threshold <= QuantumFeedbackCurve.MAX_POINTS; threshold++) {
            tooMany.add(new QuantumFeedbackCurve.Point(threshold, 0));
        }
        assertThrows(IllegalArgumentException.class, () -> new QuantumFeedbackCurve(
                QuantumFeedbackMetric.TEMPERATURE, tooMany));
    }

    @Test
    void descendingPresetIsTheExactInverseOfAscending() {
        QuantumFeedbackCurve ascending = QuantumFeedbackCurve.evenlySpaced(
                QuantumFeedbackMetric.STABILITY, 0, 10_000, false);
        QuantumFeedbackCurve descending = QuantumFeedbackCurve.evenlySpaced(
                QuantumFeedbackMetric.STABILITY, 0, 10_000, true);

        for (int index = 0; index < ascending.points().size(); index++) {
            assertEquals(15 - ascending.points().get(index).signal(),
                    descending.points().get(index).signal());
        }
    }

    @Test
    void presetsRemainValidForSubFifteenCentikelvinRanges() {
        QuantumFeedbackCurve ascending = QuantumFeedbackCurve.evenlySpaced(
                QuantumFeedbackMetric.TEMPERATURE, 30_000, 30_005, false);
        QuantumFeedbackCurve descending = QuantumFeedbackCurve.evenlySpaced(
                QuantumFeedbackMetric.TEMPERATURE, 30_000, 30_001, true);

        assertEquals(6, ascending.points().size());
        assertEquals(0, ascending.evaluate(30_000));
        assertEquals(15, ascending.evaluate(30_005));
        assertEquals(2, descending.points().size());
        assertEquals(15, descending.evaluate(30_000));
        assertEquals(0, descending.evaluate(30_001));
    }
}
