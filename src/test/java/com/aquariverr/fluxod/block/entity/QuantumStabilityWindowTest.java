package com.aquariverr.fluxod.block.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuantumStabilityWindowTest {

    @Test
    void checksOnlyOncePerGameTick() {
        QuantumStabilityWindow window = new QuantumStabilityWindow();

        assertTrue(window.beginCheck(20));
        assertFalse(window.beginCheck(20));
        assertTrue(window.beginCheck(21));
    }

    @Test
    void resetClearsCheckedTick() {
        QuantumStabilityWindow window = new QuantumStabilityWindow();
        window.beginCheck(20);

        window.reset();

        assertTrue(window.beginCheck(20));
    }
}
