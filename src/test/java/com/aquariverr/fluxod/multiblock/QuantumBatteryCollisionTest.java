package com.aquariverr.fluxod.multiblock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuantumBatteryCollisionTest {

    @Test
    void pushesEntityOutOfTheRingTube() {
        QuantumBatteryCollisionMath.Push push = QuantumBatteryCollisionMath.ringPush(
                3.0, 0.0, 0.0, 0.0, 1.0, 0.0,
                3.0, 0.5, 0.5);

        assertEquals(1.0, push.x(), 1.0E-6);
        assertEquals(0.0, push.y(), 1.0E-6);
        assertEquals(0.0, push.z(), 1.0E-6);
    }

    @Test
    void pushesTowardTheOutsideOfTheTube() {
        QuantumBatteryCollisionMath.Push push = QuantumBatteryCollisionMath.ringPush(
                2.2, 0.0, 0.0, 0.0, 1.0, 0.0,
                3.0, 0.5, 0.5);

        assertTrue(push.x() < 0.0);
        assertEquals(0.0, push.y(), 1.0E-6);
        assertEquals(0.0, push.z(), 1.0E-6);
    }

    @Test
    void ignoresPointsOutsideTheTube() {
        QuantumBatteryCollisionMath.Push push = QuantumBatteryCollisionMath.ringPush(
                4.1, 0.0, 0.0, 0.0, 1.0, 0.0,
                3.0, 0.5, 0.5);

        assertTrue(push.lengthSqr() < 1.0E-12);
    }

    @Test
    void accountsForDistanceAlongTheRingNormal() {
        QuantumBatteryCollisionMath.Push push = QuantumBatteryCollisionMath.ringPush(
                3.0, 0.75, 0.0, 0.0, 1.0, 0.0,
                3.0, 0.5, 0.5);

        assertEquals(0.0, push.x(), 1.0E-6);
        assertEquals(0.25, push.y(), 1.0E-6);
        assertEquals(0.0, push.z(), 1.0E-6);
    }
}
