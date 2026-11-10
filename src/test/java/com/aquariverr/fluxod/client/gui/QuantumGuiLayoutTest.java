package com.aquariverr.fluxod.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuantumGuiLayoutTest {

    @Test
    void scaleMatchesTheQuantumBatteryReferenceFormula() {
        float expected = Math.min(
                (1920 - QuantumGuiLayout.UI_MARGIN) * QuantumGuiLayout.SCREEN_AXIS_FRACTION
                        / QuantumGuiLayout.REFERENCE_WIDTH,
                (1080 - QuantumGuiLayout.UI_MARGIN) * QuantumGuiLayout.SCREEN_AXIS_FRACTION
                        / QuantumGuiLayout.REFERENCE_HEIGHT);

        assertEquals(expected, QuantumGuiLayout.scale(1920, 1080));
    }

    @Test
    void scaleUsesTheLimitingScreenAxis() {
        assertEquals(0.5F, QuantumGuiLayout.scale(438, 1000));
        assertEquals(0.5F, QuantumGuiLayout.scale(1000, 244));
    }

    @Test
    void scaleKeepsTheExistingLowerBoundOnTinyScreens() {
        assertEquals(0.05F, QuantumGuiLayout.scale(1, 1));
    }

    @Test
    void centeredPositionMatchesTheCurrentLogicalCoordinateFormula() {
        assertEquals(25, QuantumGuiLayout.centeredPosition(960, 430, 2.0F));
        assertEquals(1, QuantumGuiLayout.centeredPosition(433, 430, 1.0F));
        assertEquals(-45, QuantumGuiLayout.centeredPosition(340, 430, 1.0F));
    }
}
