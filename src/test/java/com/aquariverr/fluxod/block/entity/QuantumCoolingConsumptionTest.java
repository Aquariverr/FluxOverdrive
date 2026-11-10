package com.aquariverr.fluxod.block.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuantumCoolingConsumptionTest {

    @Test
    void consumesTenTimesTheStoredCoolingForTheSameOutput() {
        long requestedCooling = 80_000L;

        long storedCooling = QuantumCoolingConsumption.storedCoolingFor(requestedCooling, 10);

        assertEquals(800_000L, storedCooling);
        assertEquals(requestedCooling, QuantumCoolingConsumption.effectiveCooling(storedCooling, 10));
    }

    @Test
    void doesNotAdvertiseIncompleteMilliKelvinOfCooling() {
        assertEquals(2L, QuantumCoolingConsumption.effectiveCooling(29L, 10));
        assertEquals(0L, QuantumCoolingConsumption.effectiveCooling(9L, 10));
    }

    @Test
    void saturatesStoredCoolingConversion() {
        assertEquals(Long.MAX_VALUE, QuantumCoolingConsumption.storedCoolingFor(Long.MAX_VALUE, 10));
    }
}
