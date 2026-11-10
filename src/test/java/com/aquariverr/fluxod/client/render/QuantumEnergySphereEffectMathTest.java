package com.aquariverr.fluxod.client.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuantumEnergySphereEffectMathTest {

    @Test
    void fullStabilityPreservesTheExistingBaseline() {
        QuantumEnergySphereEffectMath.Effects effects = QuantumEnergySphereEffectMath.calculate(
                10_000, false, 0, QuantumEnergySphereEffectMath.NO_LEAK_PULSE, 120.0);

        assertEquals(1.0, effects.radiusScale(), 1.0E-9);
        assertEquals(1.0, effects.surfaceDisplacementScale(), 1.0E-9);
        assertEquals(0, effects.dischargeSlotBonus());
        assertEquals(1.0, effects.dischargeLengthScale(), 1.0E-9);
        assertEquals(1.0, effects.dischargeWidthScale(), 1.0E-9);
        assertEquals(0.0, effects.flashIntensity(), 1.0E-9);
    }

    @Test
    void instabilityProgressivelyStrengthensDischarges() {
        QuantumEnergySphereEffectMath.Effects stable = effectsAt(10_000);
        QuantumEnergySphereEffectMath.Effects warning = effectsAt(3_000);
        QuantumEnergySphereEffectMath.Effects critical = effectsAt(500);

        assertTrue(warning.dischargeSlotBonus() > stable.dischargeSlotBonus());
        assertTrue(critical.dischargeSlotBonus() > warning.dischargeSlotBonus());
        assertTrue(warning.dischargeLengthScale() > stable.dischargeLengthScale());
        assertTrue(critical.dischargeLengthScale() > warning.dischargeLengthScale());
        assertTrue(critical.dischargeWidthScale() > warning.dischargeWidthScale());
    }

    @Test
    void leakPulseAlternatesExpansionAndContractionAndBoostsArcs() {
        boolean expanded = false;
        boolean contracted = false;
        double maximumLength = 1.0;
        double maximumWidth = 1.0;
        int maximumSlots = 0;
        for (double age = 0.25; age < QuantumEnergySphereEffectMath.LEAK_PULSE_TICKS; age += 0.25) {
            QuantumEnergySphereEffectMath.Effects effects = QuantumEnergySphereEffectMath.calculate(
                    10_000, false, 37, age, 0.0);
            expanded |= effects.radiusScale() > 1.001;
            contracted |= effects.radiusScale() < 0.999;
            maximumLength = Math.max(maximumLength, effects.dischargeLengthScale());
            maximumWidth = Math.max(maximumWidth, effects.dischargeWidthScale());
            maximumSlots = Math.max(maximumSlots, effects.dischargeSlotBonus());
        }

        assertTrue(expanded);
        assertTrue(contracted);
        assertTrue(maximumLength > 1.4);
        assertTrue(maximumWidth > 1.5);
        assertTrue(maximumSlots >= 3);
    }

    @Test
    void criticalFlickerRequiresAnImminentQuench() {
        double maximumImminentFlash = 0.0;
        for (double gameTime = 0.0; gameTime < 80.0; gameTime += 0.25) {
            maximumImminentFlash = Math.max(maximumImminentFlash,
                    QuantumEnergySphereEffectMath.calculate(500, true, 4,
                            QuantumEnergySphereEffectMath.NO_LEAK_PULSE, gameTime).flashIntensity());
            assertEquals(0.0, QuantumEnergySphereEffectMath.calculate(500, false, 4,
                    QuantumEnergySphereEffectMath.NO_LEAK_PULSE, gameTime).flashIntensity(), 1.0E-9);
            assertEquals(0.0, QuantumEnergySphereEffectMath.calculate(1_000, true, 4,
                    QuantumEnergySphereEffectMath.NO_LEAK_PULSE, gameTime).flashIntensity(), 1.0E-9);
        }
        assertTrue(maximumImminentFlash > 0.35);
    }

    @Test
    void malformedInputsRemainWithinRenderBounds() {
        QuantumEnergySphereEffectMath.Effects effects = QuantumEnergySphereEffectMath.calculate(
                Integer.MIN_VALUE, true, Integer.MIN_VALUE, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY);

        assertTrue(Double.isFinite(effects.radiusScale()));
        assertTrue(effects.radiusScale() >= 0.90 && effects.radiusScale() <= 1.10);
        assertTrue(effects.dischargeSlotBonus() >= 0 && effects.dischargeSlotBonus() <= 10);
        assertTrue(effects.flashIntensity() >= 0.0 && effects.flashIntensity() <= 1.0);
    }

    @Test
    void worldVerticesAreLimitedToTheInnerRingClearance() {
        double maximum = Math.sqrt(4.5) - 0.15;
        assertEquals(1.0, QuantumBatteryRenderMath.clampWorldEffectLength(
                1.0, 0.0, 0.0, maximum), 1.0E-9);
        assertEquals(maximum, QuantumBatteryRenderMath.clampWorldEffectLength(
                3.0, 4.0, 0.0, maximum), 1.0E-9);
        assertEquals(5.0, QuantumBatteryRenderMath.clampWorldEffectLength(
                3.0, 4.0, 0.0, Double.POSITIVE_INFINITY), 1.0E-9);
    }

    private static QuantumEnergySphereEffectMath.Effects effectsAt(int stabilityBasisPoints) {
        return QuantumEnergySphereEffectMath.calculate(stabilityBasisPoints, false, 0,
                QuantumEnergySphereEffectMath.NO_LEAK_PULSE, 0.0);
    }
}
