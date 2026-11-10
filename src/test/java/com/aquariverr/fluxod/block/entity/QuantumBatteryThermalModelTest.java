package com.aquariverr.fluxod.block.entity;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuantumBatteryThermalModelTest {

    @Test
    void usesConfiguredHeatingRates() {
        assertEquals(100.0, QuantumBatteryThermalModel.heatingPerSecond(1));
        assertEquals(500.0, QuantumBatteryThermalModel.heatingPerSecond(2));
        assertEquals(2_500.0, QuantumBatteryThermalModel.heatingPerSecond(3));
        assertEquals(5.0, QuantumBatteryThermalModel.heatingPerTick(1));
        assertEquals(25.0, QuantumBatteryThermalModel.heatingPerTick(2));
        assertEquals(125.0, QuantumBatteryThermalModel.heatingPerTick(3));
    }

    @Test
    void multiplierHitsRequiredTemperatureAnchors() {
        assertEquals(0.0, QuantumBatteryThermalModel.constraintEnergyMultiplier(0.0), 1.0E-9);
        assertEquals(1.0, QuantumBatteryThermalModel.constraintEnergyMultiplier(
                QuantumBatteryThermalModel.ROOM_TEMPERATURE_K), 1.0E-9);
        assertEquals(2.0, QuantumBatteryThermalModel.constraintEnergyMultiplier(
                QuantumBatteryThermalModel.MAX_TEMPERATURE_K), 1.0E-9);
    }

    @Test
    void thermalLoadUsesRoomTemperatureAsZero() {
        assertEquals(0.0, QuantumBatteryThermalModel.thermalLoadPercent(
                QuantumBatteryThermalModel.ROOM_TEMPERATURE_K), 1.0E-9);
        assertEquals(100.0, QuantumBatteryThermalModel.thermalLoadPercent(
                QuantumBatteryThermalModel.MAX_TEMPERATURE_K), 1.0E-9);
        assertEquals(0.0, QuantumBatteryThermalModel.thermalLoadPercent(0.0), 1.0E-9);
    }

    @Test
    void multiplierIsSmoothAndMonotonic() {
        double previous = -1.0;
        for (int temperature = 0; temperature <= 40_000; temperature += 10) {
            double multiplier = QuantumBatteryThermalModel.constraintEnergyMultiplier(temperature);
            assertTrue(multiplier >= previous);
            previous = multiplier;
        }
        double belowRoom = QuantumBatteryThermalModel.constraintEnergyMultiplier(
                QuantumBatteryThermalModel.ROOM_TEMPERATURE_K - 0.01);
        double aboveRoom = QuantumBatteryThermalModel.constraintEnergyMultiplier(
                QuantumBatteryThermalModel.ROOM_TEMPERATURE_K + 0.01);
        assertTrue(Math.abs(aboveRoom - belowRoom) < 1.0E-6);
    }

    @Test
    void scalesArbitraryPrecisionConstraintCosts() {
        BigInteger base = new BigInteger("1000000000000000000000000");
        assertEquals(BigInteger.ZERO, QuantumBatteryThermalModel.scaleConstraintCost(base, 0.0));
        assertEquals(base, QuantumBatteryThermalModel.scaleConstraintCost(
                base, QuantumBatteryThermalModel.ROOM_TEMPERATURE_K));
        assertEquals(base.multiply(BigInteger.TWO), QuantumBatteryThermalModel.scaleConstraintCost(
                base, QuantumBatteryThermalModel.MAX_TEMPERATURE_K));
    }
}
