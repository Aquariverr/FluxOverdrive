package com.aquariverr.fluxod.block.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuantumStabilityGaugeTest {

    @Test
    void startsAtOneHundredPercentAndDecaysAfterTwentyUnpoweredTicks() {
        QuantumStabilityGauge gauge = new QuantumStabilityGauge();

        assertEquals(10_000, gauge.basisPoints());
        for (int tick = 0; tick < 19; tick++) {
            assertFalse(gauge.recordConstraintPower(false));
        }
        assertEquals(10_000, gauge.basisPoints());
        assertTrue(gauge.recordConstraintPower(false));
        assertEquals(9_960, gauge.basisPoints());
        assertEquals(0, gauge.unpoweredTickRemainder());
    }

    @Test
    void intermittentPowerDoesNotClearAccumulatedUnpoweredTicks() {
        QuantumStabilityGauge gauge = new QuantumStabilityGauge();

        for (int tick = 0; tick < 19; tick++) gauge.recordConstraintPower(false);
        for (int tick = 0; tick < 20; tick++) gauge.recordConstraintPower(true);
        gauge.recordConstraintPower(false);

        assertEquals(9_960, gauge.basisPoints());
    }

    @Test
    void poweredTicksRestoreStabilityAndFullRecoveryClearsRemainders() {
        QuantumStabilityGauge gauge = new QuantumStabilityGauge();
        for (int tick = 0; tick < 20; tick++) gauge.recordConstraintPower(false);
        for (int tick = 0; tick < 7; tick++) gauge.recordConstraintPower(false);

        for (int tick = 0; tick < 20; tick++) gauge.recordConstraintPower(true);

        assertEquals(10_000, gauge.basisPoints());
        assertEquals(0, gauge.unpoweredTickRemainder());
        assertEquals(0, gauge.poweredTickRemainder());
    }

    @Test
    void clampsAtZeroButCanRecoverFromZero() {
        QuantumStabilityGauge gauge = new QuantumStabilityGauge();
        for (int tick = 0; tick < 5_000; tick++) gauge.recordConstraintPower(false);

        assertEquals(0, gauge.basisPoints());
        gauge.recordConstraintPower(false);
        assertEquals(0, gauge.unpoweredTickRemainder());

        for (int tick = 0; tick < 20; tick++) gauge.recordConstraintPower(true);
        assertEquals(40, gauge.basisPoints());
    }

    @Test
    void restoresPersistedValueAndTickRemainders() {
        QuantumStabilityGauge gauge = new QuantumStabilityGauge();
        gauge.load(9_960, 19, 7);

        gauge.recordConstraintPower(false);

        assertEquals(9_920, gauge.basisPoints());
        assertEquals(0, gauge.unpoweredTickRemainder());
        assertEquals(7, gauge.poweredTickRemainder());
    }

    @Test
    void clampsInvalidPersistedValues() {
        QuantumStabilityGauge gauge = new QuantumStabilityGauge();
        gauge.load(20_000, -1, 200);

        assertEquals(10_000, gauge.basisPoints());
        assertEquals(0, gauge.unpoweredTickRemainder());
        assertEquals(19, gauge.poweredTickRemainder());
    }
}
