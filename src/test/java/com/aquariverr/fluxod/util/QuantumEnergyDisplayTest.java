package com.aquariverr.fluxod.util;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuantumEnergyDisplayTest {

    @Test
    void preservesSmallValuesAndCompactsLargeValues() {
        assertEquals("0", QuantumEnergyDisplay.from(BigInteger.ZERO).compact());
        assertEquals("999999", QuantumEnergyDisplay.from(BigInteger.valueOf(999_999L)).compact());
        assertEquals("1.000E6", QuantumEnergyDisplay.from(BigInteger.valueOf(1_000_000L)).compact());
        assertEquals("1.234E24", QuantumEnergyDisplay.from(
                new BigInteger("1234567890123456789012345")).compact());
    }

    @Test
    void logarithmicScaleCapsAtLongMaximum() {
        QuantumEnergyDisplay zero = QuantumEnergyDisplay.from(BigInteger.ZERO);
        QuantumEnergyDisplay one = QuantumEnergyDisplay.from(BigInteger.ONE);
        QuantumEnergyDisplay million = QuantumEnergyDisplay.from(BigInteger.valueOf(1_000_000L));
        QuantumEnergyDisplay maximum = QuantumEnergyDisplay.from(BigInteger.valueOf(Long.MAX_VALUE));
        QuantumEnergyDisplay beyond = QuantumEnergyDisplay.from(
                BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TEN));

        assertEquals(0, zero.logBasisPoints());
        assertTrue(one.logBasisPoints() > 0);
        assertTrue(million.logBasisPoints() > one.logBasisPoints());
        assertEquals(10_000, maximum.logBasisPoints());
        assertEquals(10_000, beyond.logBasisPoints());
    }
}
